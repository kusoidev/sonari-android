package com.kusoi.sonari.music.Services

import com.kusoi.sonari.music.Models.SonariPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
data class LyricLine(
    val id: String = UUID.randomUUID().toString(),
    val time: Double,
    val text: String,
    val startTime: Double,
    val endTime: Double? = null
)

/**
 * Lyrics fetch + parse + cache. Mirrors the Swift LyricsService: tiered lrclib lookups,
 * AI fallback, LRC parsing, plain-lyric synthesis, and a JSON cache keyed by youtubeID.
 */
object LyricsService {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class LrclibResponse(
        val trackName: String? = null,
        val artistName: String? = null,
        val duration: Double? = null,
        val syncedLyrics: String? = null,
        val plainLyrics: String? = null
    )

    suspend fun fetchSynced(
        title: String,
        artist: String?,
        duration: Double,
        onStatus: ((String) -> Unit)? = null
    ): List<LyricLine>? {
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
            cleanedTitle = cleanedTitle.replace(Regex(Regex.escape(effectiveArtist), RegexOption.IGNORE_CASE), "")
                .trim()
        }

        fun resolve(raw: String): List<LyricLine>? {
            parseLRC(raw)?.let { return it }
            val synth = synthesizeLyrics(raw, duration)
            return synth.ifEmpty { null }
        }

        /** Tier 1: exact get + exact search, raced. */
        firstOf(
            { tryGet(cleanedTitle, effectiveArtist, durationSecs) },
            { trySearch(cleanedTitle, effectiveArtist, cleanedTitle, durationSecs) }
        )?.let { raw -> resolve(raw)?.let { return it } }

        /** Tier 2: free-text search, raced. */
        val tier2 = mutableListOf<suspend () -> String?>()
        tier2.add { trySearchFreeText(cleanedTitle, cleanedTitle, durationSecs) }
        effectiveArtist?.let { artistName ->
            tier2.add { trySearchFreeText("$cleanedTitle $artistName", cleanedTitle, durationSecs) }
        }
        firstOf(*tier2.toTypedArray())?.let { raw -> resolve(raw)?.let { return it } }

        onStatus?.invoke("Asking AI to identify song...")

        val strategies = listOf(
            "Extract artist & title" to """You are a music database. From this YouTube video title, extract the song title and artist.
Reply ONLY with JSON: {"artist":"...","title":"..."}

Video title: $title""",
            "Find song name" to """Given this YouTube video title, identify the song. Return ONLY:
{"song":"...","artist":"..."}

Title: $title""",
            "Clean title" to """Clean this YouTube title to just the song name and artist. Strip all the junk.
Reply ONLY with JSON: {"artist":"...","track":"..."}

Raw: $title""",
            "Who sings this?" to """What artist or band performs the song in this YouTube video title?
Reply ONLY with JSON: {"artist":"...","song":"..."}

Video title: $title"""
        )

        val aiPairs = coroutineScope {
            strategies.map { (_, prompt) ->
                async(Dispatchers.IO) {
                    val result = aiQuery(prompt) ?: return@async null
                    val a = result["artist"] ?: result["Artist"] ?: result["singer"] ?: ""
                    val t = result["title"] ?: result["Title"] ?: result["song"]
                        ?: result["Song"] ?: result["track"] ?: result["Track"] ?: result["name"] ?: ""
                    if (a.isEmpty() || t.isEmpty()) null else a to t
                }
            }.awaitAll().filterNotNull()
        }

        val tried = mutableSetOf<String>()
        for ((aiArtist, aiTitle) in aiPairs) {
            val cleanAiTitle = cleanTitle(aiTitle)
            val key = "$aiArtist|$cleanAiTitle"
            if (!tried.add(key)) continue

            onStatus?.invoke("Searching: $aiArtist - $cleanAiTitle")

            firstOf(
                { tryGet(cleanAiTitle, aiArtist, durationSecs) },
                { trySearch(cleanAiTitle, aiArtist, cleanAiTitle, durationSecs) }
            )?.let { raw ->
                resolve(raw)?.let {
                    onStatus?.invoke("Found lyrics via AI!")
                    return it
                }
            }
        }

        onStatus?.invoke("")
        return null
    }

    private fun synthesizeLyrics(text: String, duration: Double): List<LyricLine> {
        val rawLines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val looksLikeLRC = rawLines.any { Regex("""\[\d{2}:\d{2}""").containsMatchIn(it) }
        if (looksLikeLRC || rawLines.isEmpty() || duration <= 0) return emptyList()

        val gap = duration / rawLines.size
        return rawLines.mapIndexed { i, line ->
            val time = i * gap
            LyricLine(time = time, text = line, startTime = time, endTime = time + gap)
        }
    }

    private suspend fun aiQuery(prompt: String): Map<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val body = "message=${URLEncoder.encode(prompt, "UTF-8")}&messageCount=1"
            val request = Request.Builder()
                .url("https://powerbrainai.com/chat.php")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw = response.body?.string() ?: return@use null
                val start = raw.indexOf('{')
                val end = raw.lastIndexOf('}')
                if (start < 0 || end <= start) return@use null
                val jsonStr = raw.substring(start, end + 1)
                val obj = json.decodeFromString<JsonObject>(jsonStr)
                obj.mapValues { (_, v) -> v.jsonPrimitive.content }
            }
        }.getOrNull()
    }

    fun cleanTitle(t: String): String {
        var clean = fixEncoding(t)

        clean = clean.replace(
            Regex("""[\(\[][^\)\]]*\b(official|lyric|lyrics|video|audio|mv|clip|hd|4k|remastered?|visuali[sz]er|acoustic|cover|remix|extended|version|edit)\b.*?[\)\]]""", RegexOption.IGNORE_CASE),
            ""
        )
        clean = clean.replace(
            Regex("""\s*[\(\[ ](feat\.?|ft\.?|featuring)[^\)\]]*[\)\]]""", RegexOption.IGNORE_CASE),
            ""
        )
        clean = clean.replace(Regex("""\b(sings|covers|covered by|performed by)\b""", RegexOption.IGNORE_CASE), " ")
        clean = clean.replace(Regex("""\s+by\s+[a-z0-9\s]+$""", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
        clean = clean.replace(Regex("""\s+"""), " ")
        return clean.trim()
    }

    fun cleanArtist(artist: String): String {
        var cleaned = artist
        cleaned = cleaned.replace(Regex("""\s*-\s*Topic\s*$""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""\s*VEVO\s*$""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""\s*\(Official\)""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""\s*\[Official\]""", RegexOption.IGNORE_CASE), "")

        /** Split camelCase or PascalCase names: TaylorSwift -> Taylor Swift. */
        if (!cleaned.contains(" ") &&
            cleaned.any { it.isLowerCase() } &&
            cleaned.any { it.isUpperCase() }
        ) {
            val sb = StringBuilder()
            for (ch in cleaned) {
                if (ch.isUpperCase() && sb.isNotEmpty()) sb.append(' ')
                sb.append(ch)
            }
            if (sb.contains(" ")) cleaned = sb.toString()
        }

        return cleaned.trim()
    }

    private fun fixEncoding(str: String): String {
        var fixed = str
        fixed = fixed.replace("\u00E2\u0080\u009C", "\"")
        fixed = fixed.replace("\u00E2\u0080\u009D", "\"")
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
        s.lowercase().replace(Regex("""[^a-z0-9\s]"""), "").trim()

    private fun wordOverlap(a: String, b: String): Double {
        val wa = normalize(a).split(Regex("""\s+""")).filter { it.length > 2 }.toSet()
        val wb = normalize(b).split(Regex("""\s+""")).filter { it.length > 2 }.toSet()
        if (wa.isEmpty()) return 1.0
        val matches = wa.count { wb.contains(it) }
        return matches.toDouble() / wa.size
    }

    private fun isTitleMatch(resultTrackName: String, expectedTrack: String): Boolean {
        val nResult = normalize(resultTrackName)
        val nExpected = normalize(expectedTrack)
        return nExpected.contains(nResult) || wordOverlap(expectedTrack, resultTrackName) >= 0.5
    }

    private fun isDurationOk(resultDuration: Double?, expectedDuration: Int): Boolean {
        if (expectedDuration <= 0 || resultDuration == null) return true
        return kotlin.math.abs(resultDuration.toInt() - expectedDuration) <= 1
    }

    private fun pickBest(results: List<LrclibResponse>, expectedTitle: String, durationSecs: Int): String? {
        if (results.isEmpty()) return null
        val validated = results.filter { isTitleMatch(it.trackName ?: "", expectedTitle) }
        val pool = validated.ifEmpty { results }

        pool.firstOrNull { it.syncedLyrics != null && isDurationOk(it.duration, durationSecs) }?.let { return it.syncedLyrics }
        pool.firstOrNull { it.syncedLyrics != null }?.let { return it.syncedLyrics }
        pool.firstOrNull { it.plainLyrics != null && isDurationOk(it.duration, durationSecs) }?.let { return it.plainLyrics }
        pool.firstOrNull { it.plainLyrics != null }?.let { return it.plainLyrics }
        return null
    }

    private suspend fun fetchJson(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("User-Agent", "Kusoinema/1.0").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }

    private suspend fun tryGet(trackName: String, artistName: String?, durationSecs: Int): String? {
        if (trackName.isEmpty()) return null
        var url = "https://lrclib.net/api/get?track_name=${enc(trackName)}"
        if (!artistName.isNullOrEmpty()) url += "&artist_name=${enc(artistName)}"
        if (durationSecs > 0) url += "&duration=$durationSecs"
        val raw = fetchJson(url) ?: return null
        return runCatching {
            val r = json.decodeFromString<LrclibResponse>(raw)
            r.syncedLyrics ?: r.plainLyrics
        }.getOrNull()
    }

    private suspend fun trySearch(trackName: String, artistName: String?, expectedTitle: String, durationSecs: Int): String? {
        if (trackName.isEmpty()) return null
        var url = "https://lrclib.net/api/search?track_name=${enc(trackName)}"
        if (!artistName.isNullOrEmpty()) url += "&artist_name=${enc(artistName)}"
        val raw = fetchJson(url) ?: return null
        return runCatching {
            val results = json.decodeFromString<List<LrclibResponse>>(raw)
            pickBest(results, expectedTitle, durationSecs)
        }.getOrNull()
    }

    private suspend fun trySearchFreeText(query: String, expectedTitle: String, durationSecs: Int): String? {
        if (query.isEmpty()) return null
        val raw = fetchJson("https://lrclib.net/api/search?q=${enc(query)}") ?: return null
        return runCatching {
            val results = json.decodeFromString<List<LrclibResponse>>(raw)
            pickBest(results, expectedTitle, durationSecs)
        }.getOrNull()
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** Race the tasks, return as soon as any yields a non-null result, then cancel the rest. */
    private suspend fun firstOf(vararg tasks: suspend () -> String?): String? = coroutineScope {
        if (tasks.isEmpty()) return@coroutineScope null
        val deferred = tasks.map { async(Dispatchers.IO) { it() } }.toMutableList()
        var result: String? = null
        while (deferred.any { it.isActive }) {
            val finished = select<String?> {
                deferred.forEach { d -> d.onAwait { v -> v } }
            }
            if (finished != null) {
                result = finished
                break
            }
            /** That task finished empty; drop it so select does not loop on it. */
            deferred.removeAll { it.isCompleted }
        }
        deferred.forEach { it.cancel() }
        result
    }

    private fun parseLRC(lrc: String): List<LyricLine>? {
        val lines = mutableListOf<Pair<Double, String>>()
        val regex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?\](.*)""")

        for (line in lrc.split("\n")) {
            val m = regex.find(line) ?: continue
            val mins = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val secs = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val text = m.groupValues[4].trim()
            if (text.isEmpty()) continue
            lines.add((mins * 60 + secs) to text)
        }

        if (lines.isEmpty()) return null
        lines.sortBy { it.first }

        return lines.mapIndexed { i, (time, text) ->
            val end = if (i + 1 < lines.size) lines[i + 1].first else null
            LyricLine(time = time, text = text, startTime = time, endTime = end)
        }
    }

    suspend fun loadCachedLyrics(youtubeID: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        val file = File(SonariPaths.lyricsCache, "$youtubeID.json")
        if (!file.exists()) return@withContext null
        runCatching {
            json.decodeFromString<List<LyricLine>>(file.readText())
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    suspend fun saveCachedLyrics(lines: List<LyricLine>, youtubeID: String) = withContext(Dispatchers.IO) {
        runCatching {
            File(SonariPaths.lyricsCache, "$youtubeID.json").writeText(json.encodeToString(lines))
        }
        Unit
    }

    fun saveCachedLyricsSync(lines: List<LyricLine>, youtubeID: String) {
        runCatching {
            File(SonariPaths.lyricsCache, "$youtubeID.json").writeText(json.encodeToString(lines))
        }
    }

    fun syncEditLyricLine(text: String, index: Int, lines: List<LyricLine>): List<LyricLine> {
        if (index !in lines.indices || text.isEmpty()) return lines
        val original = lines[index]
        return lines.toMutableList().also {
            it[index] = original.copy(text = text)
        }
    }

    fun deleteCachedLyrics(youtubeID: String) {
        runCatching { File(SonariPaths.lyricsCache, "$youtubeID.json").delete() }
    }
}