package com.recbox.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object CallRecordingStore {
    private const val PREFS = "call_recording"
    private const val KEY_PATH = "last_path"
    private const val KEY_NAME = "last_name"
    private const val KEY_NUMBER = "last_number"

    fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()

    fun save(context: Context, file: File, contact: ContactItem) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PATH, file.absolutePath)
            .putString(KEY_NAME, contact.name)
            .putString(KEY_NUMBER, contact.phoneNumber)
            .apply()
    }

    fun last(context: Context): Pair<File, ContactItem>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_PATH, null) ?: return null
        val file = File(path).takeIf { it.exists() } ?: return null
        val name = prefs.getString(KEY_NAME, "مخاطب") ?: "مخاطب"
        val number = prefs.getString(KEY_NUMBER, "") ?: ""
        return file to ContactItem(0L, name, number)
    }

    fun copyToGallery(context: Context, source: File, contact: ContactItem): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "تماس با ${contact.name}.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Recbox")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } }
                ?: error("Unable to open gallery output")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            uri
        }.getOrElse {
            resolver.delete(uri, null, null)
            null
        }
    }
}
