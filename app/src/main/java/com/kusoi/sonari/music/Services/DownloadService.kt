package com.kusoi.sonari.music.Services

import com.kusoi.sonari.music.Models.SonariPaths
import com.kusoi.sonari.music.Models.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Serial download queue. Mirrors the Swift DownloadService: ensure the relay has the
 * file cached, then stream it to disk with byte progress.
 */
object DownloadService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

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
            Failed("Failed");
        }
    }

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private var processing = false

    fun enqueue(videoID: String, title: String, thumbnailURL: String?, artist: String?) {
        if (LibraryManager.tracks.value.any { it.youtubeID == videoID }) return
        if (_jobs.value.any { it.videoID == videoID && it.state != Job.State.Completed && it.state != Job.State.Failed }) return

        _jobs.value = _jobs.value + Job(
            videoID = videoID,
            title = title,
            thumbnailURL = thumbnailURL,
            artist = artist
        )
        if (!processing) processQueue()
    }

    fun removeFinished() {
        _jobs.value = _jobs.value.filterNot {
            it.state == Job.State.Completed || it.state == Job.State.Failed
        }
    }

    /** Download a track and return the saved Track. Used by the player for immediate playback. */
    suspend fun fetchAndSave(
        videoID: String,
        title: String,
        thumbnailURL: String?,
        artist: String?,
        onProgress: (Double) -> Unit
    ): Track {
        val status = ensureCached(videoID, title)
        val dest = downloadFile(videoID, title, onProgress)

        val track = Track(
            title = title,
            youtubeID = videoID,
            thumbnailURL = thumbnailURL,
            artist = artist,
            fileName = dest.name,
            fileSize = status.fileSize?.toLong()
        )
        LibraryManager.addTrack(track)
        return track
    }

    private fun processQueue() {
        if (processing) return
        processing = true
        Thread {
            try {
                while (true) {
                    val idx = _jobs.value.indexOfFirst { it.state == Job.State.Queued }
                    if (idx < 0) break
                    processJob(_jobs.value[idx].id)
                }
            } finally {
                processing = false
            }
        }.start()
    }

    private fun processJob(jobID: String) {
        val job = _jobs.value.firstOrNull { it.id == jobID } ?: return
        update(jobID) { it.copy(state = Job.State.Preparing) }

        kotlinx.coroutines.runBlocking {
            try {
                val status = ensureCached(job.videoID, job.title)
                update(jobID) { it.copy(state = Job.State.Downloading) }

                val dest = downloadFile(job.videoID, job.title) { p ->
                    update(jobID) { it.copy(progress = p) }
                }

                val track = Track(
                    title = job.title,
                    youtubeID = job.videoID,
                    thumbnailURL = job.thumbnailURL,
                    artist = job.artist,
                    fileName = dest.name,
                    fileSize = status.fileSize?.toLong()
                )
                LibraryManager.addTrack(track)
                update(jobID) { it.copy(state = Job.State.Completed, progress = 1.0) }
            } catch (e: Exception) {
                update(jobID) { it.copy(state = Job.State.Failed) }
            }
        }
    }

    private fun update(jobID: String, transform: (Job) -> Job) {
        _jobs.value = _jobs.value.map { if (it.id == jobID) transform(it) else it }
    }

    private suspend fun ensureCached(videoID: String, title: String): ApiService.StatusResponse {
        val status = ApiService.checkCache(videoID)
        if (status.cached) return status

        ApiService.requestDownload(videoID, title)

        repeat(120) {
            delay(500)
            val s = ApiService.checkCache(videoID)
            if (s.cached) return s
        }
        throw ApiService.ApiError.jobTimeout
    }

    private suspend fun downloadFile(
        videoID: String,
        title: String,
        onProgress: (Double) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val url = ApiService.serveURL(videoID)
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiService.ApiError.serverError
            val body = response.body ?: throw ApiService.ApiError.serverError
            val total = body.contentLength()

            val safeTitle = title
                .replace(Regex("""[^\w\s\-]"""), "")
                .trim()
                .replace(" ", "_")
            val dest = File(SonariPaths.documents, "${safeTitle}_$videoID.m4a")

            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress((written.toDouble() / total).coerceIn(0.0, 1.0))
                    }
                }
            }
            dest
        }
    }
}