package com.kusoi.sonari.music

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * The Flutter port wrote to <filesDir>/app_flutter. This app uses filesDir
 * directly. Rather than orphan an existing library on upgrade, adopt it once:
 * move the tracks and lyric cache over, then rewrite library.json with
 * durations filled in from the m4a files (the Flutter save dropped them).
 */
object LegacyFlutterMigration {

    private const val FLAG = "sonari.migratedFlutter"

    fun run() {
        if (AppPaths.prefs.getBoolean(FLAG, false)) return
        val legacy = File(AppPaths.context.filesDir, "app_flutter")
        if (!legacy.exists()) {
            AppPaths.prefs.edit().putBoolean(FLAG, true).apply()
            return
        }

        val legacyLibrary = File(legacy, "library.json")
        if (legacyLibrary.exists()) {
            val tracks = Json.tracksFromJson(legacyLibrary.readText()).map { track ->
                track.fileName?.let { name ->
                    val from = File(legacy, name)
                    val to = File(AppPaths.dataDirectory, name)
                    if (from.exists() && !to.exists()) from.renameTo(to)
                }
                val file = track.localFileURL
                val duration = track.duration ?: file?.let { readDuration(it) }
                track.copy(duration = duration)
            }
            LibraryStore.replaceAll((LibraryStore.tracks.value + tracks).distinctBy { it.id })
        }

        copyMissing(File(legacy, "lyrics_cache"), AppPaths.lyricsCacheDirectory)
        copyMissing(File(legacy, "thumbnails"), AppPaths.thumbnailCacheDirectory)

        AppPaths.prefs.edit().putBoolean(FLAG, true).apply()
    }

    private fun copyMissing(from: File, to: File) {
        if (!from.isDirectory) return
        from.listFiles()?.forEach { file ->
            val dest = File(to, file.name)
            if (!dest.exists()) file.copyTo(dest, overwrite = false)
        }
    }

    private fun readDuration(file: File): Double? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            ms?.takeIf { it > 0 }?.let { it / 1000.0 }
        } finally {
            retriever.release()
        }
    }.getOrNull()
}
