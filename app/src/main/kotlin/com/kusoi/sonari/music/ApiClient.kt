package com.kusoi.sonari.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Mirrors Sonari/Services/APIService.swift. Same relay, same token header. */
object ApiClient {
    var baseURL: String = "https://REDACTED-HOST"
    var authToken: String = "REDACTED"

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    data class StatusResponse(val cached: Boolean, val fileSize: Long?, val streamURL: String?)

    suspend fun search(query: String): List<YouTubeSearchResult> = withContext(Dispatchers.IO) {
        val url = "$baseURL/search?q=${query.urlEncoded()}"
        val request = Request.Builder()
            .url(url)
            .header("X-Auth-Token", authToken)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("search failed: ${response.code}")
            Json.searchResultsFromJson(response.body?.string().orEmpty())
        }
    }

    suspend fun requestDownload(videoID: String, title: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("videoId", videoID)
            put("title", title)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseURL/request")
            .header("X-Auth-Token", authToken)
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("request failed: ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            json.optString("jobId")
        }
    }

    suspend fun checkCache(videoID: String): StatusResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseURL/status/$videoID")
            .header("X-Auth-Token", authToken)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("status failed: ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            StatusResponse(
                cached = json.optBoolean("cached", false),
                fileSize = if (json.has("fileSize")) json.optLong("fileSize") else null,
                streamURL = if (json.has("streamURL") && !json.isNull("streamURL")) json.optString("streamURL") else null
            )
        }
    }

    fun serveURL(videoID: String): String = "$baseURL/serve/$videoID?token=$authToken"

    /** Pulls the 11-char video id out of any YouTube URL shape, or a bare id. */
    fun extractVideoID(urlString: String): String? {
        val pattern = Regex("(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/shorts/|youtube\\.com/embed/|m\\.youtube\\.com/watch\\?v=)([a-zA-Z0-9_-]{11})")
        pattern.find(urlString)?.let { return it.groupValues[1] }
        return if (Regex("^[a-zA-Z0-9_-]{11}$").matches(urlString.trim())) urlString.trim() else null
    }
}

fun String.urlEncoded(): String = java.net.URLEncoder.encode(this, "UTF-8")
