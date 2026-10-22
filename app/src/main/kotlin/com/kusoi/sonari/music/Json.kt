package com.kusoi.sonari.music

import org.json.JSONArray
import org.json.JSONObject

/**
 * Hand-rolled JSON rather than kotlinx.serialization so the keys land exactly
 * where the iOS Codable encoder puts them. library.json stays portable.
 */
object Json {

    fun tracksToJson(tracks: List<Track>): String {
        val arr = JSONArray()
        tracks.forEach { arr.put(trackToJson(it)) }
        return arr.toString()
    }

    fun tracksFromJson(text: String): List<Track> = runCatching {
        val arr = JSONArray(text)
        (0 until arr.length()).map { trackFromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun playlistsToJson(playlists: List<Playlist>): String {
        val arr = JSONArray()
        playlists.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("trackIDs", JSONArray(p.trackIDs))
                put("createdAt", p.createdAt)
            })
        }
        return arr.toString()
    }

    fun playlistsFromJson(text: String): List<Playlist> = runCatching {
        val arr = JSONArray(text)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Playlist(
                id = o.optString("id"),
                name = o.optString("name"),
                trackIDs = o.optJSONArray("trackIDs").toStringList(),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }.getOrDefault(emptyList())

    fun lyricsToJson(lines: List<LyricLine>): String {
        val arr = JSONArray()
        lines.forEach { l ->
            arr.put(JSONObject().apply {
                put("time", l.time)
                put("text", l.text)
                put("startTime", l.startTime)
                l.endTime?.let { put("endTime", it) }
            })
        }
        return arr.toString()
    }

    fun lyricsFromJson(text: String): List<LyricLine> = runCatching {
        val arr = JSONArray(text)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val time = o.optDouble("time", 0.0)
            LyricLine(
                time = time,
                text = o.optString("text"),
                startTime = o.optDouble("startTime", time),
                endTime = o.optDoubleOrNull("endTime")
            )
        }
    }.getOrDefault(emptyList())

    fun searchResultsFromJson(text: String): List<YouTubeSearchResult> = runCatching {
        val root = JSONObject(text)
        val arr = root.optJSONArray("results") ?: return emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            YouTubeSearchResult(
                id = o.optString("id"),
                title = o.optString("title"),
                thumbnailURL = o.optStringOrNull("thumbnailURL"),
                duration = o.optStringOrNull("duration"),
                channelName = o.optStringOrNull("channelName")
            )
        }
    }.getOrDefault(emptyList())

    private fun trackToJson(t: Track) = JSONObject().apply {
        put("id", t.id)
        put("title", t.title)
        put("youtubeID", t.youtubeID)
        t.thumbnailURL?.let { put("thumbnailURL", it) }
        t.artist?.let { put("artist", it) }
        t.duration?.takeIf { it.isFinite() }?.let { put("duration", it) }
        t.fileName?.let { put("fileName", it) }
        t.fileSize?.let { put("fileSize", it) }
        put("addedAt", t.addedAt)
        put("playlistIDs", JSONArray(t.playlistIDs))
        t.streamingURL?.let { put("streamingURL", it) }
        put("playCount", t.playCount)
    }

    private fun trackFromJson(o: JSONObject) = Track(
        id = o.optString("id"),
        title = o.optString("title"),
        youtubeID = o.optString("youtubeID"),
        thumbnailURL = o.optStringOrNull("thumbnailURL"),
        artist = o.optStringOrNull("artist"),
        duration = o.optDoubleOrNull("duration"),
        fileName = o.optStringOrNull("fileName"),
        fileSize = o.optLongOrNull("fileSize"),
        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
        playlistIDs = o.optJSONArray("playlistIDs").toStringList(),
        streamingURL = o.optStringOrNull("streamingURL"),
        playCount = o.optInt("playCount", 0)
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)

    /** JSON null and malformed numbers both come back as null, never NaN. */
    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return if (v.isFinite()) v else null
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return optLong(key, -1L).takeIf { it >= 0 }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it) }
    }
}
