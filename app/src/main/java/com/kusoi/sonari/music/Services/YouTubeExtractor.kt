package com.kusoi.sonari.music.Services

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeSearchResult(
    val id: String,
    val title: String,
    val thumbnailURL: String? = null,
    val duration: String? = null,
    val channelName: String? = null
)

/** URL parsing and search entry point, mirroring the Swift YouTubeExtractor. */
object YouTubeExtractor {

    private val watchRegex = Regex(
        """(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/shorts/|youtube\.com/embed/|m\.youtube\.com/watch\?v=)([a-zA-Z0-9_-]{11})"""
    )
    private val bareIdRegex = Regex("""^[a-zA-Z0-9_-]{11}$""")

    /** Pull an 11-char video id from any YouTube URL shape, or a bare id. */
    fun extractVideoID(urlString: String): String? {
        watchRegex.find(urlString)?.let { return it.groupValues[1] }

        val trimmed = urlString.trim()
        if (bareIdRegex.matches(trimmed)) return trimmed

        return null
    }

    suspend fun search(query: String): List<YouTubeSearchResult> =
        ApiService.search(query)
}