package com.recbox.app.data

import android.media.MediaMetadataRetriever
import android.content.Context
import java.io.File

data class ArchivedMedia(
    val file: File,
    val mode: RecordingMode,
    val durationSeconds: Long,
    val createdAtMillis: Long,
)

object ArchiveRepository {
    fun load(context: Context): List<ArchivedMedia> {
        val folders = listOf(
            RecordingMode.AmbientAudio to "audio",
            RecordingMode.Video to "videos",
            RecordingMode.Photo to "photos",
            RecordingMode.Call to "calls",
        )
        return folders
            .flatMap { (mode, folderName) ->
                listOf(context.filesDir, context.cacheDir)
                    .map { root -> mode to File(root, folderName) }
            }
            .flatMap { (mode, folder) -> folder.listFiles().orEmpty().map { mode to it } }
            .filter { (_, file) -> file.isFile && file.length() > 0L }
            .distinctBy { (_, file) -> file.absolutePath }
            .map { (mode, file) ->
                ArchivedMedia(
                    file = file,
                    mode = mode,
                    durationSeconds = if (mode == RecordingMode.Photo) 0L else durationSeconds(file),
                    createdAtMillis = file.lastModified(),
                )
            }
            .sortedByDescending { it.createdAtMillis }
    }

    private fun durationSeconds(file: File): Long = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            val milliseconds = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            (milliseconds + 999L) / 1000L
        }
    }.getOrDefault(0L)
}
