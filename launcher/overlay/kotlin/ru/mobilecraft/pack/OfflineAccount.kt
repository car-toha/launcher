package ru.mobilecraft.pack

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Вход по нику для сервера в offline-режиме (online-mode=false).
 *
 * UUID считается так же, как его считает сам сервер для пиратских игроков:
 * UUID.nameUUIDFromBytes("OfflinePlayer:<ник>"). Поэтому инвентарь, права и
 * приваты игрока на сервере не «потеряются» при переустановке приложения.
 */
data class OfflineAccount(val nickname: String) {

    val uuid: UUID =
        UUID.nameUUIDFromBytes("OfflinePlayer:$nickname".toByteArray(StandardCharsets.UTF_8))

    /** Аргументы для командной строки игры вместо данных Microsoft-аккаунта. */
    fun launchArgs(): Map<String, String> = mapOf(
        "auth_player_name" to nickname,
        "auth_uuid" to uuid.toString().replace("-", ""),
        "auth_access_token" to "0",
        "user_type" to "legacy",
        "auth_xuid" to "0",
        "clientid" to "0",
    )

    companion object {
        private const val PREFS = "mobilecraft_account"
        private const val KEY_NICK = "nickname"

        /** Правила ника Minecraft: 3–16 символов, латиница, цифры и «_». */
        private val NICK_REGEX = Regex("^[A-Za-z0-9_]{3,16}$")

        /** null — ник подходит, иначе текст ошибки для поля ввода. */
        fun validate(nick: String): String? = when {
            nick.length < 3 -> "Ник должен быть не короче 3 символов"
            nick.length > 16 -> "Ник должен быть не длиннее 16 символов"
            !NICK_REGEX.matches(nick) -> "Только латинские буквы, цифры и _"
            else -> null
        }

        fun load(context: Context): OfflineAccount? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_NICK, null)
                ?.takeIf { validate(it) == null }
                ?.let(::OfflineAccount)

        fun save(context: Context, nick: String): OfflineAccount {
            require(validate(nick) == null) { "Некорректный ник: $nick" }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_NICK, nick).apply()
            return OfflineAccount(nick)
        }
    }
}
