package com.kusoi.sonari.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Port of Sonari/Services/DownloadService.swift. Jobs run one at a time. The
 * byte download uses a generous read timeout: a 172MB track over a slow link
 * blew past the old 5 minute cap and never saved.
 */
object DownloadService {

    data class Job(
        val id: String = UUID.randomUUID().toString(),
        val videoID: String,
        val title: String,
        val thumbnailURL: String?,
        val artist: String?,
        val state: State = State.Queued,
        val progress: Double = 0.0
    ) {
        enum class State(val label: String) {
            Queued("Queued"),
            Preparing("Preparing"),
            Downloading("Downloading"),
            Completed("Saved"),
            Failed("Failed")
        }

        val isFinished: Boolean
            get() = state == State.Completed || state == State.Failed
    }

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private var isProcessing = false
    @Volatile private var failureMessage: String? = null

    /** Downloads are slow, so the transfer gets its own long-timeout client. */
    private val downloadClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .callTimeout(15, TimeUnit.MINUTES)
            .build()
    }

    fun enqueue(videoID: String, title: String, thumbnailURL: String?, artist: String?) {
        if (LibraryStore.tracks.value.any { it.youtubeID == videoID }) return
        if (_jobs.value.any { it.videoID == videoID && !it.isFinished }) return
        _jobs.value = _jobs.value + Job(
            videoID = videoID,
            title = title,
            thumbnailURL = thumbnailURL,
            artist = artist
        )
        if (!isProcessing) {
            isProcessing = true
            Thread { processQueue() }.start()
        }
    }

    fun removeFinished() {
        _jobs.value = _jobs.value.filterNot { it.isFinished }
    }

    /** Direct download used by the player so it can start as soon as the file lands. */
    suspend fun fetchAndSave(
        videoID: String,
        title: String,
        thumbnailURL: String?,
        artist: String?,
        onProgress: (Double) -> Unit
    ): Track = withContext(Dispatchers.IO) {
        val status = ensureCached(videoID, title)
        val dest = downloadFile(videoID, title, onProgress)
        val track = Track(
            title = title,
            youtubeID = videoID,
            thumbnailURL = thumbnailURL,
            artist = artist,
            fileName = dest.name,
            fileSize = status.fileSize
        )
        withContext(Dispatchers.Main) { LibraryStore.addTrack(track) }
        track
    }

    private fun processQueue() {
        try {
            while (true) {
                val idx = _jobs.value.indexOfFirst { it.state == Job.State.Queued }
                if (idx < 0) break
                processJob(idx)
            }
        } finally {
            isProcessing = false
        }
    }

    private fun processJob(idx: Int) {
        val snapshot = _jobs.value.getOrNull(idx) ?: return
        onMain { updateJob(snapshot.id) { it.copy(state = Job.State.Preparing) } }
        try {
            val status = kotlinx.coroutines.runBlocking { ensureCached(snapshot.videoID, snapshot.title) }
            onMain { updateJob(snapshot.id) { it.copy(state = Job.State.Downloading) } }

            val dest = kotlinx.coroutines.runBlocking {
                downloadFile(snapshot.videoID, snapshot.title) { p ->
                    onMain { updateJob(snapshot.id) { it.copy(progress = p) } }
                }
            }

            val track = Track(
                title = snapshot.title,
                youtubeID = snapshot.videoID,
                thumbnailURL = snapshot.thumbnailURL,
                artist = snapshot.artist,
                fileName = dest.name,
                fileSize = status.fileSize
            )
            LibraryStore.addTrack(track)
            onMain { updateJob(snapshot.id) { it.copy(state = Job.State.Completed, progress = 1.0) } }
        } catch (e: Exception) {
            failureMessage = e.message
            onMain { updateJob(snapshot.id) { it.copy(state = Job.State.Failed) } }
        }
    }

    /** The queue thread owns the download; StateFlow updates hop to main. */
    private fun onMain(block: () -> Unit) =
        kotlinx.coroutines.runBlocking(Dispatchers.Main) { block() }

    private fun updateJob(id: String, transform: (Job) -> Job) {
        _jobs.value = _jobs.value.map { if (it.id == id) transform(it) else it }
    }

    private suspend fun ensureCached(videoID: String, title: String): ApiClient.StatusResponse {
        val status = ApiClient.checkCache(videoID)
        if (status.cached) return status

        ApiClient.requestDownload(videoID, title)
        // Poll at 500ms, cap at 60s. The relay usually caches in 2-5s.
        repeat(120) {
            delay(500)
            val s = ApiClient.checkCache(videoID)
            if (s.cached) return s
        }
        throw IOException("job timed out")
    }

    private fun downloadFile(videoID: String, title: String, onProgress: (Double) -> Unit): File {
        val url = ApiClient.serveURL(videoID)
        val safeTitle = title
            .replace(Regex("[^\\w\\s\\-]"), "")
            .trim()
            .replace(" ", "_")
        val dest = File(AppPaths.dataDirectory, "${safeTitle}_$videoID.m4a")
        val temp = File(AppPaths.dataDirectory, "${safeTitle}_$videoID.part")

        val request = Request.Builder().url(url).build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("download failed: ${response.code}")
            val body = response.body ?: throw IOException("empty body")
            val total = body.contentLength()
            temp.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress((written.toDouble() / total).coerceIn(0.0, 1.0))
                    }
                }
            }
        }

        if (dest.exists()) dest.delete()
        if (!temp.renameTo(dest)) throw IOException("could not move download into place")
        return dest
    }
}
