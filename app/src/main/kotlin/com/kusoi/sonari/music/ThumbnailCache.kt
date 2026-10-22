package com.kusoi.sonari.music

import android.graphics.BitmapFactory
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Disk cache for artwork. Same job as Sonari/Services/ThumbnailCache.swift:
 * check disk first so offline still shows art, otherwise fetch once and keep.
 */
object ThumbnailCache {

    fun cachedFile(youtubeID: String): File? =
        File(AppPaths.thumbnailCacheDirectory, "$youtubeID.jpg").takeIf { it.exists() }

    suspend fun ensureCached(youtubeID: String, remoteURL: String?): File? = withContext(Dispatchers.IO) {
        cachedFile(youtubeID)?.let { return@withContext it }
        if (remoteURL.isNullOrBlank()) return@withContext null
        runCatching {
            val request = okhttp3.Request.Builder().url(remoteURL).build()
            ApiClient.http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bytes = response.body?.bytes() ?: return@runCatching null
                val dest = File(AppPaths.thumbnailCacheDirectory, "$youtubeID.jpg")
                val temp = File(AppPaths.thumbnailCacheDirectory, "$youtubeID.tmp")
                temp.writeBytes(bytes)
                temp.renameTo(dest)
                dest
            }
        }.getOrNull()
    }

    /** Decodes from disk off the main thread; used by the theme sampler. */
    suspend fun bytes(youtubeID: String, remoteURL: String?): ByteArray? = withContext(Dispatchers.IO) {
        val file = ensureCached(youtubeID, remoteURL) ?: return@withContext null
        runCatching { file.readBytes() }.getOrNull()
    }

    /** Preloads the next couple of rows so scrolling does not flash placeholders. */
    suspend fun preload(track: Track) {
        ensureCached(track.youtubeID, track.thumbnailURL)
    }

    fun isValidImage(file: File): Boolean = runCatching {
        BitmapFactory.decodeFile(file.absolutePath) != null
    }.getOrDefault(false)
}
