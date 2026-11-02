package com.myagent.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File

/** Koi bhi AI-call jo task-loop ke bahar chahiye: available APIs me se pehli jo chale. */
object Ai {
    fun ask(prefs: Prefs, msgs: List<Msg>, maxTok: Int = 1500): String {
        val slots = Slots.available(prefs)
        if (slots.isEmpty()) throw ApiException(-2, "Koi API ON nahi / key khaali (Settings)")
        var last: ApiException? = null
        for (s in slots) {
            try {
                return ApiClient.chat(s, prefs, msgs, maxTok)
            } catch (e: ApiException) {
                last = e
            }
        }
        throw last ?: ApiException(-2, "API fail")
    }
}

/** Files phone ke Download/MyAgent/<folder> me save hoti hain (Files app me dikhengi). */
object FileStore {
    fun save(ctx: Context, folder: String, name: String, content: String): String =
        saveBytes(ctx, folder, name, content.toByteArray(Charsets.UTF_8))

    fun saveBytes(ctx: Context, folder: String, name: String, data: ByteArray): String {
        val f = folder.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "General" }
        val n = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "file.txt" }
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues()
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, n)
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/MyAgent/$f")
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
                ?: throw Exception("file nahi ban saki")
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(data) }
            return "Download/MyAgent/$f/$n"
        }
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "MyAgent/$f")
        dir.mkdirs()
        val file = File(dir, n)
        file.writeBytes(data)
        return file.absolutePath
    }
}
