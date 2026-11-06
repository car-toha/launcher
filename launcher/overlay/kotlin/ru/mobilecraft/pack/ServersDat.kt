package ru.mobilecraft.pack

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Пишет servers.dat (несжатый NBT), чтобы сервер сразу был в списке «Сетевая игра».
 * Формат: TAG_Compound "" { TAG_List "servers" of TAG_Compound { name, ip, acceptTextures } }
 */
object ServersDat {

    private const val TAG_END = 0
    private const val TAG_BYTE = 1
    private const val TAG_STRING = 8
    private const val TAG_LIST = 9
    private const val TAG_COMPOUND = 10

    fun write(gameDir: File, name: String, address: String) {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(TAG_COMPOUND); out.writeUTF("")
            out.writeByte(TAG_LIST); out.writeUTF("servers")
            out.writeByte(TAG_COMPOUND); out.writeInt(1)

            out.writeByte(TAG_STRING); out.writeUTF("name"); out.writeUTF(name)
            out.writeByte(TAG_STRING); out.writeUTF("ip"); out.writeUTF(address)
            // 1 = всегда принимать ресурспак сервера, без лишнего диалога на телефоне
            out.writeByte(TAG_BYTE); out.writeUTF("acceptTextures"); out.writeByte(1)
            out.writeByte(TAG_END)   // конец записи сервера

            out.writeByte(TAG_END)   // конец корня
        }
        File(gameDir, "servers.dat").writeBytes(bytes.toByteArray())
    }
}
