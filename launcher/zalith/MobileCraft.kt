/*
 * MobileCraft — неофициальная модифицированная версия Zalith Launcher 2.
 * Распространяется под GNU GPL-3.0, как и оригинал.
 */
package com.movtery.zalithlauncher.mobilecraft

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.AccountType
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.getUUIDFromUserName
import com.movtery.zalithlauncher.game.account.isLocalAccount
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.ui.guide.GuideKeys
import com.movtery.zalithlauncher.ui.guide.GuideProgress
import com.movtery.zalithlauncher.viewmodel.LaunchGameViewModel
import com.movtery.zalithlauncher.viewmodel.ModpackImportOperation
import com.movtery.zalithlauncher.viewmodel.ModpackImportViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import org.json.JSONObject
import java.io.File

object MobileCraft {
    /** Имя единственной игровой версии. */
    const val VERSION_NAME = "MobileCraft"

    /** Пока не null — импорт модпака не спрашивает имя версии и мобильный интернет. */
    @Volatile
    var autoVersionName: String? = null

    /** Показать полный интерфейс Zalith (настройки, редактор управления). */
    val showFullLauncher = mutableStateOf(false)

    private const val PREFS = "mobilecraft"
    private const val KEY_NICK = "nickname"
    private const val KEY_REVISION = "pack_revision"
    private const val KEY_MIC_ASKED = "mic_asked"

    /** То, что при обновлении сборки переносим из старой папки игры. */
    private val KEEP_ON_UPDATE = { name: String ->
        name != "mods" && name != "servers.dat" && !name.startsWith(VERSION_NAME)
    }

    data class PackInfo(val serverName: String, val address: String, val revision: String)

    fun packInfo(context: Context): PackInfo {
        val text = context.assets.open("mobilecraft/pack.json").bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        return PackInfo(json.getString("server_name"), json.getString("address"), json.getString("revision"))
    }

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** null — ник подходит; иначе текст ошибки. */
    fun validateNick(nick: String): String? = when {
        nick.length < 3 -> "Ник — минимум 3 символа"
        nick.length > 16 -> "Ник — максимум 16 символов"
        !Regex("^[A-Za-z0-9_]+$").matches(nick) -> "Только латинские буквы, цифры и _"
        else -> null
    }

    /** Локальный (офлайн) аккаунт с этим ником; UUID — как у сервера в offline-режиме. */
    private suspend fun ensureAccount(nick: String) {
        val existing = AccountsManager.accountsFlow.value
            .find { it.isLocalAccount() && it.username == nick }
        if (existing != null) {
            AccountsManager.setCurrentAccount(existing)
        } else {
            AccountsManager.suspendSaveAccount(
                Account(
                    username = nick,
                    profileId = getUUIDFromUserName(nick).toString(),
                    accountType = AccountType.LOCAL.tag,
                )
            )
        }
    }

    private suspend fun findVersion(name: String): Version? {
        VersionsManager.waitForRefresh()
        return VersionsManager.versions.value.find { it.getVersionName() == name && it.isValid() }
    }

    /**
     * Ставит/обновляет сборку из assets/mobilecraft/pack.mrpack.
     * При обновлении сохраняет миры, настройки, схемы Litematica и т.д.
     */
    private suspend fun installPack(
        context: Context,
        pack: PackInfo,
        importer: ModpackImportViewModel,
        onStatus: (String) -> Unit,
    ): Version {
        val old = findVersion(VERSION_NAME)
        var backup: File? = null
        if (old != null) {
            onStatus("Обновляю сборку…")
            backup = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "mobilecraft_backup").apply { FileUtils.deleteQuietly(this) }
                old.getVersionPath().listFiles()?.filter { KEEP_ON_UPDATE(it.name) }?.forEach {
                    if (it.isDirectory) FileUtils.copyDirectoryToDirectory(it, dir)
                    else FileUtils.copyFileToDirectory(it, dir)
                }
                dir
            }
            VersionsManager.deleteVersion(old)
            VersionsManager.waitForRefresh()
        } else {
            onStatus("Устанавливаю игру…")
        }

        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "mobilecraft.mrpack").also { out ->
                context.assets.open("mobilecraft/pack.mrpack").use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
        }

        val done = CompletableDeferred<Unit>()
        autoVersionName = VERSION_NAME
        try {
            withContext(Dispatchers.Main) {
                importer.import(context, Uri.fromFile(file), onStop = { done.complete(Unit) })
            }
            done.await()
        } finally {
            autoVersionName = null
        }

        val result = importer.importOperation
        if (result is ModpackImportOperation.Error) throw result.th
        if (result !is ModpackImportOperation.Finished) error("Установка отменена")
        importer.importOperation = ModpackImportOperation.None

        VersionsManager.refresh("MobileCraft.installPack", VERSION_NAME)
        var version: Version? = null
        repeat(40) {
            if (version == null) {
                delay(250)
                version = findVersion(VERSION_NAME)
            }
        }
        version ?: error("Сборка не установилась")

        if (backup != null) withContext(Dispatchers.IO) {
            val target = version!!.getVersionPath()
            backup.listFiles()?.forEach {
                if (it.isDirectory) FileUtils.copyDirectoryToDirectory(it, target)
                else FileUtils.copyFileToDirectory(it, target)
            }
            FileUtils.deleteQuietly(backup)
        }

        prefs(context).edit().putString(KEY_REVISION, pack.revision).apply()
        return version!!
    }

    suspend fun play(
        context: Context,
        nick: String,
        launcher: LaunchGameViewModel,
        importer: ModpackImportViewModel,
        onStatus: (String) -> Unit,
    ) {
        val pack = packInfo(context)
        onStatus("Вход…")
        ensureAccount(nick)

        val installed = findVersion(VERSION_NAME)
        val upToDate = prefs(context).getString(KEY_REVISION, null) == pack.revision
        val version = if (installed != null && upToDate) installed
                      else installPack(context, pack, importer, onStatus)

        onStatus("Запуск…")
        withContext(Dispatchers.Main) {
            launcher.quickPlayServer(version, pack.address)
        }
    }

    internal fun nick(context: Context) = prefs(context).getString(KEY_NICK, "") ?: ""
    internal fun saveNick(context: Context, nick: String) =
        prefs(context).edit().putString(KEY_NICK, nick).apply()
    internal fun micAsked(context: Context) = prefs(context).getBoolean(KEY_MIC_ASKED, false)
    internal fun setMicAsked(context: Context) =
        prefs(context).edit().putBoolean(KEY_MIC_ASKED, true).apply()
}

/** Главный экран: ник и «Играть». */
@Composable
fun MobileHome(
    activity: ComponentActivity,
    launchGameViewModel: LaunchGameViewModel,
    modpackImportViewModel: ModpackImportViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pack = remember { MobileCraft.packInfo(context) }

    var nick by rememberSaveable { mutableStateOf(MobileCraft.nick(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    // Обучающий тур по интерфейсу Zalith здесь не нужен
    LaunchedEffect(Unit) {
        if (!GuideProgress.isPlayed(GuideKeys.Main)) {
            GuideProgress.markPlayed(GuideKeys.Main, GuideKeys.Main.version)
        }
    }

    val startGame: () -> Unit = {
        scope.launch {
            try {
                MobileCraft.play(context, nick, launchGameViewModel, modpackImportViewModel) { status = it }
            } catch (e: Throwable) {
                error = "Ошибка: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                status = null
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startGame() }

    val onPlay: () -> Unit = onPlay@{
        val problem = MobileCraft.validateNick(nick)
        if (problem != null) {
            error = problem
            return@onPlay
        }
        error = null
        MobileCraft.saveNick(context, nick)
        val granted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted && !MobileCraft.micAsked(context)) {
            MobileCraft.setMicAsked(context)
            micPermission.launch(Manifest.permission.RECORD_AUDIO) // для голосового чата
        } else {
            startGame()
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("MobileCraft", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(pack.serverName, style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = nick,
                    onValueChange = { nick = it.trim().take(16); error = null },
                    label = { Text("Ник") },
                    singleLine = true,
                    enabled = status == null,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                if (status != null) {
                    CircularProgressIndicator()
                    Text(status!!)
                } else {
                    Button(onClick = onPlay, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Играть", fontSize = 18.sp)
                    }
                }

                TextButton(onClick = { MobileCraft.showFullLauncher.value = true }, enabled = status == null) {
                    Text("Настройки и управление")
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "Неофициальная модифицированная версия Zalith Launcher 2 (GPL-3.0)",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Кнопка возврата с полного интерфейса Zalith на экран MobileCraft. */
@Composable
fun MobileBackButton() {
    if (!MobileCraft.showFullLauncher.value) return
    Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomStart) {
        Button(onClick = { MobileCraft.showFullLauncher.value = false }) {
            Text("← MobileCraft")
        }
    }
}
