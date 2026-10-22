package com.kusoi.sonari.music

import androidx.compose.ui.graphics.Color
import java.io.File
import java.util.UUID

/**
 * Mirrors Sonari/Models/Track.swift. Field names match the iOS JSON keys so a
 * library.json or lyric cache written on one platform reads on the other.
 */
data class Track(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val youtubeID: String,
    val thumbnailURL: String? = null,
    val artist: String? = null,
    val duration: Double? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val playlistIDs: List<String> = emptyList(),
    val streamingURL: String? = null,
    val playCount: Int = 0
) {
    val localFile: File?
        get() = fileName?.let { File(AppPaths.dataDirectory, it) }

    val localFileURL: File?
        get() = localFile?.takeIf { it.exists() }

    val isDownloaded: Boolean
        get() = localFileURL != null

    val formattedDuration: String
        get() = duration?.let { formatTime(it) } ?: "--:--"
}

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trackIDs: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/** One timed lyric line. startTime/endTime carry over from the iOS cache format. */
data class LyricLine(
    val time: Double,
    val text: String,
    val startTime: Double = time,
    val endTime: Double? = null
)

/** Search hit from the relay. Matches YouTubeSearchResult in Swift. */
data class YouTubeSearchResult(
    val id: String,
    val title: String,
    val thumbnailURL: String? = null,
    val duration: String? = null,
    val channelName: String? = null
)

/**
 * Four colors derived from the current artwork. Same maths as
 * Sonari/Models/AppTheme.swift, so both apps tint themselves identically.
 */
data class AppTheme(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val accent: Color,
    val onAccent: Color
) {
    companion object {
        val Dark = AppTheme(
            backgroundTop = Color(0.08f, 0.08f, 0.09f),
            backgroundBottom = Color(0.03f, 0.03f, 0.04f),
            accent = Color.White,
            onAccent = Color.Black
        )
    }
}

fun formatTime(t: Double): String {
    if (!t.isFinite() || t < 0) return "0:00"
    val total = t.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

enum class LoopMode { None, One, All }

enum class LyricsMode {
    Karaoke,
    Plain;

    companion object {
        private const val KEY = "sonari.lyricsMode"

        fun load(): LyricsMode {
            val raw = AppPaths.prefs.getString(KEY, null) ?: return Karaoke
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Karaoke
        }
    }

    fun save() = AppPaths.prefs.edit().putString(KEY, name).apply()
}

object AppPaths {
    /** Set once from Application.onCreate so plain objects can reach Context. */
    lateinit var context: android.content.Context

    /** Files live in internal storage; the relay token is the only credential. */
    val dataDirectory: File by lazy {
        File(context.filesDir, "").apply { mkdirs() }
    }

    val lyricsCacheDirectory: File by lazy {
        File(dataDirectory, "lyrics_cache").apply { mkdirs() }
    }

    val thumbnailCacheDirectory: File by lazy {
        File(dataDirectory, "thumbs").apply { mkdirs() }
    }

    val prefs by lazy {
        context.getSharedPreferences("sonari", 0)
    }
}

/** Titles look like "Artist - Song (Official Video)". This strips the junk. */
object TrackText {
    private val parenJunk = Regex("\\(.*?\\)")
    private val bracketJunk = Regex("\\[.*?\\]")

    fun clean(raw: String): String =
        raw.replace(parenJunk, "").replace(bracketJunk, "").trim()

    fun titleDisplay(track: Track): String {
        val parts = track.title.split(" - ")
        val titlePart = if (parts.size >= 2) parts.drop(1).joinToString(" - ") else track.title
        return clean(titlePart)
    }

    fun artistDisplay(track: Track): String {
        track.artist?.takeIf { it.isNotBlank() }?.let { return it }
        val parts = track.title.split(" - ")
        if (parts.size < 2) return "Unknown Artist"
        return clean(parts[0])
    }
}
