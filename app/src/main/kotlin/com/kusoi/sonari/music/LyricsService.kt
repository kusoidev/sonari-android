package com.kusoi.sonari.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Port of Sonari/Services/LyricsService.swift. Tier 1 exact get + exact search,
 * tier 2 free text, then the AI fallback. Caches under lyrics_cache/<id>.json
 * in the same shape the iOS app writes.
 */
object LyricsService {

    suspend fun fetchSynced(
        title: String,
        artist: String?,
        duration: Double,
        onStatus: (String) -> Unit = {}
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val durationSecs = if (duration.isFinite() && duration > 0) duration.toInt() else 0

        var cleanedTitle = cleanTitle(title)
        var effectiveArtist = artist

        if ((effectiveArtist.isNullOrEmpty() || effectiveArtist.lowercase().contains("unknown")) && title.contains("-")) {
            val parts = title.split("-").map { it.trim() }
            if (parts.size >= 2) {
                effectiveArtist = parts[0]
                cleanedTitle = cleanTitle(parts[1])
            }
        }
        if (!effectiveArtist.isNullOrEmpty()) {
            effectiveArtist = cleanArtist(effectiveArtist)
        }
        if (!effectiveArtist.isNullOrEmpty()) {
            cleanedTitle = cleanedTitle.replace(Regex(Regex.escape(effectiveArtist), RegexOption.IGNORE_CASE), "").trim()
        }

        fun resolve(raw: String?): List<LyricLine>? {
            if (raw == null) return null
            parseLRC(raw)?.let { return it }
            val synthesized = synthesizeLyrics(raw, duration)
            return synthesized.ifEmpty { null }
        }

        // Tier 1: exact get and exact search raced, first hit wins.
        firstOf(listOf(
            { tryGet(cleanedTitle, effectiveArtist, durationSecs) },
            { trySearch(cleanedTitle, effectiveArtist, cleanedTitle, durationSecs) }
        ))?.let { raw ->
            resolve(raw)?.let { return@withContext it }
        }

        // Tier 2: free-text search.
        val tier2 = mutableListOf<suspend () -> String?>()
        tier2.add { trySearchFreeText(cleanedTitle, cleanedTitle, durationSecs) }
        if (!effectiveArtist.isNullOrEmpty()) {
            tier2.add { trySearchFreeText("$cleanedTitle $effectiveArtist", cleanedTitle, durationSecs) }
        }
        firstOf(tier2)?.let { raw ->
            resolve(raw)?.let { return@withContext it }
        }

        onStatus("Asking AI to identify song...")

        val prompts = listOf(
            "You are a music database. From this YouTube video title, extract the song title and artist.\nReply ONLY with JSON: {\"artist\":\"...\",\"title\":\"...\"}\n\nVideo title: $title",
            "Given this YouTube video title, identify the song. Return ONLY:\n{\"song\":\"...\",\"artist\":\"...\"}\n\nTitle: $title",
            "Clean this YouTube title to just the song name and artist. Strip all the junk.\nReply ONLY with JSON: {\"artist\":\"...\",\"track\":\"...\"}\n\nRaw: $title",
            "What artist or band performs the song in this YouTube video title?\nReply ONLY with JSON: {\"artist\":\"...\",\"song\":\"...\"}\n\nVideo title: $title"
        )

        val pairs = coroutineScope {
            prompts.map { prompt ->
                async {
                    val result = aiQuery(prompt) ?: return@async null
                    val aiArtist = result["artist"] ?: result["Artist"] ?: result["singer"] ?: ""
                    val aiTrack = result["title"] ?: result["Title"] ?: result["song"] ?: result["Song"]
                        ?: result["track"] ?: result["Track"] ?: result["name"] ?: ""
                    if (aiArtist.isBlank() || aiTrack.isBlank()) null else Pair(aiArtist, aiTrack)
                }
            }.awaitAll().filterNotNull()
        }

        val tried = mutableSetOf<String>()
        for ((aiArtist, aiTrack) in pairs) {
            val aiTitle = cleanTitle(aiTrack)
            if (!tried.add("$aiArtist|$aiTitle")) continue
            onStatus("Searching: $aiArtist - $aiTitle")
            val raw = firstOf(listOf(
                { tryGet(aiTitle, aiArtist, durationSecs) },
                { trySearch(aiTitle, aiArtist, aiTitle, durationSecs) }
            ))
            resolve(raw)?.let {
                onStatus("Found lyrics via AI!")
                return@withContext it
            }
        }

        onStatus("")
        null
    }

    /** Returns the first non-null result and cancels the rest. */
    private suspend fun firstOf(tasks: List<suspend () -> String?>): String? = coroutineScope {
        if (tasks.isEmpty()) return@coroutineScope null
        val channel = Channel<String?>(Channel.UNLIMITED)
        val children = tasks.map { task ->
            launch(Dispatchers.IO) { channel.send(task()) }
        }
        var result: String? = null
        repeat(tasks.size) {
            val candidate = channel.receive()
            if (candidate != null && result == null) result = candidate
        }
        children.forEach { it.cancel() }
        channel.close()
        result
    }

    private fun synthesizeLyrics(text: String, duration: Double): List<LyricLine> {
        val rawLines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val looksLikeLRC = rawLines.any { Regex("\\[\\d{2}:\\d{2}").containsMatchIn(it) }
        if (looksLikeLRC || rawLines.isEmpty() || duration <= 0) return emptyList()
        val gap = duration / rawLines.size
        return rawLines.mapIndexed { i, line ->
            val time = i * gap
            LyricLine(time = time, text = line, startTime = time, endTime = time + gap)
        }
    }

    private fun aiQuery(prompt: String): Map<String, String>? = runCatching {
        val body = ("message=" + prompt.urlEncoded() + "&messageCount=1")
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val request = Request.Builder().url("https://powerbrainai.com/chat.php").post(body).build()
        ApiClient.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val raw = response.body?.string() ?: return@runCatching null
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return@runCatching null
            val json = JSONObject(raw.substring(start, end + 1))
            val map = mutableMapOf<String, String>()
            json.keys().forEach { map[it] = json.optString(it) }
            map
        }
    }.getOrNull()
    fun cleanTitle(t: String): String {
        var clean = fixEncoding(t)
        clean = clean.replace(
            Regex("[\\(\\[][^\\)\\]]*\\b(official|lyric|lyrics|video|audio|mv|clip|hd|4k|remastered?|visuali[sz]er|acoustic|cover|remix|extended|version|edit)\\b.*?[\\)\\]]", RegexOption.IGNORE_CASE),
            ""
        )
        clean = clean.replace(Regex("\\s*[\\(\\[ ](feat\\.?|ft\\.?|featuring)[^\\)\\]]*[\\)\\]]", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("\\b(sings|covers|covered by|performed by)\\b", RegexOption.IGNORE_CASE), " ")
        clean = clean.replace(Regex("\\s+by\\s+[a-z0-9\\s]+$", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("[^a-z0-9\\s]", RegexOption.IGNORE_CASE), " ")
        clean = clean.replace(Regex("\\s+"), " ")
        return clean.trim()
    }

    fun cleanArtist(artist: String): String {
        var cleaned = artist
        cleaned = cleaned.replace(Regex("\\s*-\\s*Topic\\s*$", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("\\s*VEVO\\s*$", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("\\s*\\(Official\\)", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("\\s*\\[Official\\]", RegexOption.IGNORE_CASE), "")
        // TaylorSwift -> Taylor Swift
        if (!cleaned.contains(" ") && cleaned.any { it.isLowerCase() } && cleaned.any { it.isUpperCase() }) {
            val split = buildString {
                cleaned.forEach { c ->
                    if (c.isUpperCase() && isNotEmpty()) append(' ')
                    append(c)
                }
            }
            if (split.contains(" ")) cleaned = split
        }
        return cleaned.trim()
    }

    private fun fixEncoding(str: String): String {
        var fixed = str
        fixed = fixed.replace("\u00E2\u0080\u009C", "\"").replace("\u00E2\u0080\u009D", "\"")
        fixed = fixed.replace("\u00E2\u0080\u0099", "'")
        fixed = fixed.replace("\u0393\u00C7\u00A3", "\"")
        fixed = fixed.replace("\u0393\u00C7\u00A5", "\"")
        fixed = fixed.replace("\u0393\u00C7\u00A8", "\"")
        fixed = fixed.replace("\u0393\u00C7\u00A4", "\"")
        fixed = fixed.replace("\u0393\u00C7\u00A9", "\"")
        fixed = fixed.replace("\u0393\u00C7\u00B6", "'")
        return fixed
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim()

    private fun wordOverlap(a: String, b: String): Double {
        val wa = normalize(a).split(" ").filter { it.length > 2 }.toSet()
        val wb = normalize(b).split(" ").filter { it.length > 2 }.toSet()
        if (wa.isEmpty()) return 1.0
        return wa.count { it in wb }.toDouble() / wa.size
    }

    private fun isTitleMatch(resultTrackName: String, expected: String): Boolean {
        val nResult = normalize(resultTrackName)
        val nExpected = normalize(expected)
        return nExpected.contains(nResult) || wordOverlap(expected, resultTrackName) >= 0.5
    }

    private fun isDurationOk(resultDuration: Double?, expectedSecs: Int): Boolean {
        if (expectedSecs <= 0 || resultDuration == null) return true
        return kotlin.math.abs(resultDuration.toInt() - expectedSecs) <= 1
    }

    private data class LrcLibResult(
        val trackName: String?,
        val artistName: String?,
        val duration: Double?,
        val syncedLyrics: String?,
        val plainLyrics: String?
    )

    private fun parseResults(json: String): List<LrcLibResult> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LrcLibResult(
                trackName = o.optStringOrNull("trackName"),
                artistName = o.optStringOrNull("artistName"),
                duration = if (o.has("duration")) o.optDouble("duration") else null,
                syncedLyrics = o.optStringOrNull("syncedLyrics"),
                plainLyrics = o.optStringOrNull("plainLyrics")
            )
        }
    }.getOrDefault(emptyList())

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun pickBest(results: List<LrcLibResult>, expectedTitle: String, durationSecs: Int): String? {
        if (results.isEmpty()) return null
        val validated = results.filter { isTitleMatch(it.trackName ?: "", expectedTitle) }
        val pool = if (validated.isEmpty()) results else validated
        pool.firstOrNull { it.syncedLyrics != null && isDurationOk(it.duration, durationSecs) }?.let { return it.syncedLyrics }
        pool.firstOrNull { it.syncedLyrics != null }?.let { return it.syncedLyrics }
        pool.firstOrNull { it.plainLyrics != null && isDurationOk(it.duration, durationSecs) }?.let { return it.plainLyrics }
        pool.firstOrNull { it.plainLyrics != null }?.let { return it.plainLyrics }
        return null
    }

    private fun httpGet(url: String): String? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", "Kusoinema/1.0").build()
        ApiClient.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }.getOrNull()

    private fun tryGet(trackName: String, artistName: String?, durationSecs: Int): String? {
        if (trackName.isEmpty()) return null
        var url = "https://lrclib.net/api/get?track_name=${trackName.urlEncoded()}"
        if (!artistName.isNullOrEmpty()) url += "&artist_name=${artistName.urlEncoded()}"
        if (durationSecs > 0) url += "&duration=$durationSecs"
        val raw = httpGet(url) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            o.optStringOrNull("syncedLyrics") ?: o.optStringOrNull("plainLyrics")
        }.getOrNull()
    }

    private fun trySearch(trackName: String, artistName: String?, expectedTitle: String, durationSecs: Int): String? {
        if (trackName.isEmpty()) return null
        var url = "https://lrclib.net/api/search?track_name=${trackName.urlEncoded()}"
        if (!artistName.isNullOrEmpty()) url += "&artist_name=${artistName.urlEncoded()}"
        val raw = httpGet(url) ?: return null
        return pickBest(parseResults(raw), expectedTitle, durationSecs)
    }

    private fun trySearchFreeText(query: String, expectedTitle: String, durationSecs: Int): String? {
        if (query.isEmpty()) return null
        val raw = httpGet("https://lrclib.net/api/search?q=${query.urlEncoded()}") ?: return null
        return pickBest(parseResults(raw), expectedTitle, durationSecs)
    }
    fun parseLRC(lrc: String): List<LyricLine>? {
        val regex = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\](.*)")
        val collected = lrc.split("\n").mapNotNull { line ->
            val m = regex.find(line) ?: return@mapNotNull null
            val mins = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val secs = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val fracRaw = m.groupValues[3]
            // Sub-second stamps: two digits are centiseconds, three are millis.
            val frac = when {
                fracRaw.isEmpty() -> 0.0
                fracRaw.length == 2 -> fracRaw.toDouble() / 100.0
                else -> fracRaw.toDouble() / 1000.0
            }
            val text = m.groupValues[4].trim()
            if (text.isEmpty()) null else Pair(mins * 60 + secs + frac, text)
        }.sortedBy { it.first }

        if (collected.isEmpty()) return null
        return collected.mapIndexed { i, (time, text) ->
            val end = if (i + 1 < collected.size) collected[i + 1].first else null
            LyricLine(time = time, text = text, startTime = time, endTime = end)
        }
    }

    fun cacheFile(youtubeID: String) = File(AppPaths.lyricsCacheDirectory, "$youtubeID.json")

    fun loadCached(youtubeID: String): List<LyricLine>? {
        val file = cacheFile(youtubeID)
        if (!file.exists()) return null
        val lines = Json.lyricsFromJson(file.readText())
        return lines.ifEmpty { null }
    }

    fun saveCached(lines: List<LyricLine>, youtubeID: String) {
        runCatching {
            val file = cacheFile(youtubeID)
            val temp = File(file.parentFile, "$youtubeID.tmp")
            temp.writeText(Json.lyricsToJson(lines))
            temp.renameTo(file)
        }
    }

    fun editLine(lines: List<LyricLine>, index: Int, newText: String): List<LyricLine> {
        if (index !in lines.indices || newText.isBlank()) return lines
        val updated = lines.toMutableList()
        updated[index] = lines[index].copy(text = newText)
        return updated
    }

    fun deleteCached(youtubeID: String) {
        cacheFile(youtubeID).delete()
    }
}
