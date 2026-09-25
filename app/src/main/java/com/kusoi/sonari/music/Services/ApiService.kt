package com.kusoi.sonari.music.Services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Talks to the Sonari backend relay. Mirrors the Swift APIService: same endpoints,
 * same X-Auth-Token header, same request/poll/serve flow.
 */
object ApiService {
    var baseURL: String = "https://REDACTED-HOST"
    var authToken: String = "REDACTED"

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class SearchResponse(val results: List<YouTubeSearchResult>)

    @Serializable
    private data class RequestResponse(@SerialName("jobId") val jobId: String)

    @Serializable
    data class StatusResponse(
        val cached: Boolean = false,
        @SerialName("fileSize") val fileSize: Int? = null,
        @SerialName("streamURL") val streamURL: String? = null
    )

    suspend fun search(query: String): List<YouTubeSearchResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("$baseURL/search?q=$encoded")
            .header("X-Auth-Token", authToken)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiError.serverError
            val body = response.body?.string() ?: throw ApiError.serverError
            json.decodeFromString<SearchResponse>(body).results
        }
    }

    suspend fun requestDownload(videoID: String, title: String): String = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(DownloadRequest(videoID, title))
        val request = Request.Builder()
            .url("$baseURL/request")
            .header("X-Auth-Token", authToken)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiError.serverError
            val body = response.body?.string() ?: throw ApiError.serverError
            json.decodeFromString<RequestResponse>(body).jobId
        }
    }

    suspend fun checkCache(videoID: String): StatusResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseURL/status/$videoID")
            .header("X-Auth-Token", authToken)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiError.serverError
            val body = response.body?.string() ?: throw ApiError.serverError
            json.decodeFromString<StatusResponse>(body)
        }
    }

    fun serveURL(videoID: String): String =
        "$baseURL/serve/$videoID?token=$authToken"

    @Serializable
    private data class DownloadRequest(
        @SerialName("videoId") val videoId: String,
        val title: String
    )

    enum class ApiError : Exception() {
        badURL, serverError, jobTimeout
    }
}