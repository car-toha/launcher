package ru.mobilecraft.pack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Готовит единственную игровую сборку: Fabric 26.1.2 + моды + сервер в списке.
 *
 * Вызывается лаунчером перед запуском (см. INTEGRATION.md). Ванильные файлы 26.1.2
 * (клиент, библиотеки, ассеты) докачивает штатный установщик Zalith, когда видит
 * профиль Fabric с "inheritsFrom": "26.1.2" — сами мы их не распространяем.
 *
 * Моды берутся с Modrinth во время установки, а не лежат в APK: у Simple Voice Chat
 * лицензия All Rights Reserved, перепаковывать его нельзя.
 */
class ServerPackProvisioner(
    private val context: Context,
    private val gameDir: File,                 // .minecraft экземпляра
    private val log: (String) -> Unit = {},
) {
    private val pack: JSONObject =
        JSONObject(context.assets.open("serverpack/serverpack.json").bufferedReader().readText())

    val profileId: String get() = pack.getString("profile_id")

    /** Полная установка/обновление. Идемпотентна: повторный вызов докачает только отличающееся. */
    fun provision() {
        installFabricProfile()
        syncMods()
        val server = pack.getJSONObject("server")
        ServersDat.write(gameDir, server.getString("name"), server.getString("address"))
        log("Готово")
    }

    // ---------- Fabric ----------

    private fun installFabricProfile() {
        val mc = pack.getString("minecraft_version")
        val loader = pack.getString("fabric_loader")
        val dir = File(gameDir, "versions/$profileId").apply { mkdirs() }
        val json = File(dir, "$profileId.json")
        if (json.exists()) return
        log("Fabric $loader для $mc")
        val profile = httpGet("https://meta.fabricmc.net/v2/versions/loader/$mc/$loader/profile/json")
        json.writeText(profile)
    }

    // ---------- Моды ----------

    private fun syncMods() {
        val modsDir = File(gameDir, "mods").apply { mkdirs() }
        val wanted = mutableSetOf<String>()
        val list = pack.getJSONArray("mods")

        for (i in 0 until list.length()) {
            val entry = list.getJSONObject(i)
            when {
                entry.has("modrinth") -> wanted += downloadFromModrinth(entry.getString("modrinth"), modsDir)
                entry.has("asset") -> wanted += copyAsset(entry.getString("asset"), modsDir)
            }
        }
        // Чужие/старые моды убираем: сборка должна совпадать с сервером
        modsDir.listFiles()?.filter { it.isFile && it.name !in wanted }?.forEach {
            log("Удаляю лишний мод ${it.name}"); it.delete()
        }
    }

    private fun downloadFromModrinth(slug: String, modsDir: File): String {
        val mc = pack.getString("minecraft_version")
        val q = "loaders=" + enc("[\"fabric\"]") + "&game_versions=" + enc("[\"$mc\"]")
        val versions = JSONArray(httpGet("https://api.modrinth.com/v2/project/$slug/version?$q"))
        require(versions.length() > 0) { "На Modrinth нет $slug для Fabric $mc" }

        // Берём самый свежий релиз, если релизов нет — самую свежую версию
        var chosen: JSONObject? = null
        for (i in 0 until versions.length()) {
            val v = versions.getJSONObject(i)
            if (v.getString("version_type") == "release") { chosen = v; break }
        }
        val version = chosen ?: versions.getJSONObject(0)

        val files = version.getJSONArray("files")
        var file = files.getJSONObject(0)
        for (i in 0 until files.length()) if (files.getJSONObject(i).optBoolean("primary")) file = files.getJSONObject(i)

        val name = file.getString("filename")
        val sha1 = file.getJSONObject("hashes").getString("sha1")
        val target = File(modsDir, name)
        if (target.exists() && sha1(target) == sha1) return name

        log("Скачиваю $name")
        val tmp = File(modsDir, "$name.part")
        download(file.getString("url"), tmp)
        check(sha1(tmp) == sha1) { "Контрольная сумма $name не совпала" }
        tmp.renameTo(target)
        return name
    }

    private fun copyAsset(path: String, modsDir: File): String {
        val name = path.substringAfterLast('/')
        context.assets.open(path).use { input -> File(modsDir, name).outputStream().use { input.copyTo(it) } }
        return name
    }

    // ---------- Сеть ----------

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            // Modrinth требует осмысленный User-Agent
            setRequestProperty("User-Agent", "MobileCraft-Launcher/1.0")
        }

    private fun httpGet(url: String): String = open(url).run {
        check(responseCode == 200) { "HTTP $responseCode: $url" }
        inputStream.bufferedReader().use { it.readText() }
    }

    private fun download(url: String, to: File) = open(url).run {
        check(responseCode == 200) { "HTTP $responseCode: $url" }
        inputStream.use { input -> to.outputStream().use { input.copyTo(it) } }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun sha1(f: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        f.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
