package com.kusoi.sonari.music.Services

import com.kusoi.sonari.music.Models.SonariPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Disk cache for YouTube thumbnails keyed by youtubeID. Downloads once, serves from disk after.
 * Mirrors the Swift ThumbnailCache, including in-flight dedupe for the same id.
 */
object ThumbnailCache {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, File>()

    fun cachedURL(youtubeID: String): File? {
        val file = File(SonariPaths.thumbnails, "$youtubeID.jpg")
        return if (file.exists()) file else null
    }

    /** Ensures the thumbnail is cached. Returns the local file when ready, null on failure. */
    suspend fun ensureCached(youtubeID: String, remoteURL: String): File? {
        cachedURL(youtubeID)?.let { return it }

        mutex.withLock { inFlight[youtubeID] }?.let { return it }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(remoteURL).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null
                    val dest = File(SonariPaths.thumbnails, "$youtubeID.jpg")
                    body.byteStream().use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    dest
                }
            }.getOrNull()
        }

        mutex.withLock {
            /** Only remember successes. A failed download must be retryable. */
            if (result != null) inFlight[youtubeID] = result else inFlight.remove(youtubeID)
        }
        return result
    }

    suspend fun preload(youtubeID: String, thumbnailURL: String?) {
        val url = thumbnailURL ?: return
        ensureCached(youtubeID, url)
    }
}