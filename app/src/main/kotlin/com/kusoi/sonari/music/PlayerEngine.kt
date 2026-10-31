package com.kusoi.sonari.music

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Port of Sonari/ViewModels/PlayerViewModel.swift plus the AVPlayer engine.
 * The 1/30s position ticker, the pure eased reveal curve, and the lyric seek
 * hold all carry over unchanged because they define the feel of the app.
 */
class PlayerEngine(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) handleSongEnded()
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                _isPlaying.value = isPlayingNow
            }

            override fun onPlayerError(error: PlaybackException) {
                _isDownloading.value = false
                _isLoadingStream.value = false
            }
        })
    }

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTime = MutableStateFlow(0.0)
    val currentTime: StateFlow<Double> = _currentTime.asStateFlow()

    private val _duration = MutableStateFlow(0.0)
    val duration: StateFlow<Double> = _duration.asStateFlow()

    private val _isSeeking = MutableStateFlow(false)
    val isSeeking: StateFlow<Boolean> = _isSeeking.asStateFlow()

    private val _isLoadingStream = MutableStateFlow(false)
    val isLoadingStream: StateFlow<Boolean> = _isLoadingStream.asStateFlow()

    private val _loopMode = MutableStateFlow(LoopMode.None)
    val loopMode: StateFlow<LoopMode> = _loopMode.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(0)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    private val _isFetchingLyrics = MutableStateFlow(false)
    val isFetchingLyrics: StateFlow<Boolean> = _isFetchingLyrics.asStateFlow()

    private val _lyricsSearchStatus = MutableStateFlow("")
    val lyricsSearchStatus: StateFlow<String> = _lyricsSearchStatus.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0.0)
    val downloadProgress: StateFlow<Double> = _downloadProgress.asStateFlow()

    private val _theme = MutableStateFlow(AppTheme.Dark)
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

    private var tickerJob: Job? = null
    private var lyricsJob: Job? = null
    private var hasRecordedPlay = false

    /** After a lyric tap the tapped index wins for a moment so the real
        position cannot flap the highlight backward. */
    private var lyricSeekHoldIndex: Int? = null
    private var lyricSeekHoldUntil: Long = 0L

    /** Live playback position for per-frame karaoke math, not the sampled tick. */
    fun smoothTime(): Double {
        if (_isSeeking.value) return _currentTime.value
        val live = player.currentPosition / 1000.0
        return if (live.isFinite()) live else _currentTime.value
    }

    fun play(track: Track) {
        if (_currentTrack.value?.id == track.id) {
            // Same track: resume, but make sure the room matches it. A cold
            // start begins at AppTheme.Dark and would otherwise stay there.
            scope.launch {
                ThumbnailCache.preload(track)
                refreshTheme(track)
            }
            togglePlayPause()
            return
        }

        _currentTrack.value = track
        _theme.value = AppTheme.Dark
        _currentTime.value = 0.0
        _duration.value = 0.0
        _lyrics.value = emptyList()
        _currentLyricIndex.value = 0
        _isFetchingLyrics.value = false
        _isDownloading.value = false
        _downloadProgress.value = 0.0
        hasRecordedPlay = false
        lyricSeekHoldIndex = null
        lyricsJob?.cancel()

        // Lyrics and theme depend on the track, not on the audio file, so start
        // them here. Waiting for playFile left a downloading track with no
        // lyrics and an untinted background until the file landed.
        scope.launch {
            ThumbnailCache.preload(track)
            refreshTheme(track)
        }
        fetchLyrics(track, forceRefresh = false)

        val local = track.localFileURL
        if (local != null) {
            playFile(local)
            return
        }

        // A different library entry may already hold the file for this video.
        val existing = LibraryStore.tracks.value.firstOrNull {
            it.youtubeID == track.youtubeID && it.localFileURL != null
        }
        if (existing != null) {
            _currentTrack.value = existing
            playFile(existing.localFileURL!!)
            return
        }

        _isLoadingStream.value = true
        _isDownloading.value = true
        _downloadProgress.value = 0.0
        scope.launch {
            runCatching {
                val saved = DownloadService.fetchAndSave(
                    videoID = track.youtubeID,
                    title = track.title,
                    thumbnailURL = track.thumbnailURL,
                    artist = track.artist
                ) { p -> _downloadProgress.value = p }
                _currentTrack.value = saved
                _isLoadingStream.value = false
                _isDownloading.value = false
                _downloadProgress.value = 0.0
                saved.localFileURL?.let { playFile(it) }
            }.onFailure { error ->
                android.util.Log.e("SonariPlayer", "download failed for " + track.youtubeID, error)
                _isLoadingStream.value = false
                _isDownloading.value = false
                _downloadProgress.value = 0.0
            }
        }
    }

    private fun playFile(file: java.io.File) {
        val track = _currentTrack.value
        val item = if (track != null) {
            MediaItem.Builder()
                .setUri(file.toURI().toString())
                .setMediaMetadata(track.toMetadata())
                .build()
        } else {
            MediaItem.fromUri(file.toURI().toString())
        }
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
        recordCurrentPlay()
        startTicker()
    }

    /** Re-pushes the current track's metadata onto the player. The media
     *  session reads it for the notification and ColorOS Fluid Cloud alert. */
    fun publishMetadata() {
        val track = _currentTrack.value ?: return
        val current = player.currentMediaItem ?: return
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            current.buildUpon().setMediaMetadata(track.toMetadata()).build()
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        // 1/30s, same cadence as the AVPlayer periodic observer. Anything
        // slower switches the lyric line late and the sweep starts mid-line.
        tickerJob = scope.launch {
            while (true) {
                delay(33)
                if (_isSeeking.value) continue
                val pos = player.currentPosition / 1000.0
                if (pos.isFinite() && pos >= 0) _currentTime.value = pos
                val dur = player.duration / 1000.0
                if (dur.isFinite() && dur > 0) _duration.value = dur
                updateLyricIndex()
            }
        }
    }

    private fun handleSongEnded() {
        when (_loopMode.value) {
            LoopMode.One -> { player.seekTo(0); player.play() }
            LoopMode.All -> nextTrack()
            LoopMode.None -> stop()
        }
    }

    /**
     * Re-samples the sleeve of the current track. Called on start and resume
     * so the whole app is tinted by the music even when the user has not
     * tapped anything yet this session.
     */
    fun refreshCurrentTheme() {
        val track = _currentTrack.value ?: return
        scope.launch {
            ThumbnailCache.preload(track)
            refreshTheme(track)
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seek(to: Double) {
        val maxDur = if (_duration.value > 0) _duration.value else (_currentTrack.value?.duration ?: to)
        val clamped = max(0.0, min(if (to.isFinite()) to else 0.0, if (maxDur > 0) maxDur else to))
        _isSeeking.value = true
        player.seekTo((clamped * 1000).toLong())
        _currentTime.value = clamped
        _isSeeking.value = false
    }

    /** Jump to a tapped lyric and pin the highlight for a moment. */
    fun seekToLyric(line: LyricLine) {
        val idx = _lyrics.value.indexOfFirst { it.time == line.time && it.text == line.text }
        if (idx >= 0) {
            _currentLyricIndex.value = idx
            lyricSeekHoldIndex = idx
            lyricSeekHoldUntil = System.currentTimeMillis() + 1000
        }
        _isSeeking.value = true
        player.seekTo((line.startTime * 1000).toLong())
        _currentTime.value = line.startTime
        _isSeeking.value = false
    }

    fun toggleLoop() {
        _loopMode.value = when (_loopMode.value) {
            LoopMode.None -> LoopMode.One
            LoopMode.One -> LoopMode.All
            LoopMode.All -> LoopMode.None
        }
    }

    fun nextTrack() {
        val q = _queue.value
        if (q.isEmpty()) { player.seekTo(0); player.play(); return }
        val next = if (_queueIndex.value < q.size - 1) _queueIndex.value + 1 else 0
        _queueIndex.value = next
        playFromQueue(q[next])
    }

    fun previousTrack() {
        val q = _queue.value
        if (q.isEmpty()) return
        val prev = if (_queueIndex.value > 0) _queueIndex.value - 1 else q.size - 1
        _queueIndex.value = prev
        playFromQueue(q[prev])
    }

    private fun playFromQueue(track: Track) {
        if (_currentTrack.value?.id == track.id) {
            player.seekTo(0)
            player.play()
            return
        }
        play(track)
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        _queue.value = tracks
        _queueIndex.value = max(0, min(startIndex, tracks.size - 1))
        _isShuffled.value = false
    }

    /** Shuffle the rest without interrupting the current track. */
    fun shuffleQueue() {
        val q = _queue.value
        if (q.size <= 1) return
        val current = q[_queueIndex.value]
        val rest = q.filterNot { it.id == current.id }.shuffled()
        _queue.value = listOf(current) + rest
        _queueIndex.value = 0
        _isShuffled.value = true
    }

    fun playTrackAt(index: Int) {
        val q = _queue.value
        if (index !in q.indices) return
        _queueIndex.value = index
        play(q[index])
    }

    fun stop() {
        player.pause()
        player.clearMediaItems()
        tickerJob?.cancel()
        _currentTrack.value = null
        _isPlaying.value = false
        _currentTime.value = 0.0
        _duration.value = 0.0
        _loopMode.value = LoopMode.None
        _theme.value = AppTheme.Dark
        _lyrics.value = emptyList()
        _isFetchingLyrics.value = false
        _isDownloading.value = false
        _downloadProgress.value = 0.0
        hasRecordedPlay = false
        lyricSeekHoldIndex = null
        lyricsJob?.cancel()
        _queue.value = emptyList()
        _queueIndex.value = 0
    }

    fun release() {
        tickerJob?.cancel()
        lyricsJob?.cancel()
        player.release()
        scope.cancel()
    }

    private fun recordCurrentPlay() {
        val track = _currentTrack.value ?: return
        if (hasRecordedPlay) return
        hasRecordedPlay = true
        LibraryStore.recordPlay(track)
    }

    /** Recompute the theme from the track artwork off the main thread. */
    private suspend fun refreshTheme(track: Track) {
        val targetID = track.id
        val bytes = ThumbnailCache.bytes(track.youtubeID, track.thumbnailURL)
        val newTheme = if (bytes != null) {
            kotlinx.coroutines.withContext(Dispatchers.Default) { ThemeSampler.from(bytes) }
        } else {
            AppTheme.Dark
        }
        if (_currentTrack.value?.id != targetID) return
        _theme.value = newTheme
    }

    // MARK: - Lyrics

    private fun fetchLyrics(track: Track, forceRefresh: Boolean) {
        _isFetchingLyrics.value = true
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            val fetchID = track.id

            val extractedArtist = track.artist?.takeIf { it.isNotBlank() } ?: run {
                val parts = track.title.split(" - ")
                if (parts.size >= 2) parts[0].trim() else null
            }
            val trackDuration = track.duration ?: 180.0

            if (!forceRefresh) {
                val cached = LyricsService.loadCached(track.youtubeID)
                if (cached != null && _currentTrack.value?.id == fetchID) {
                    _lyrics.value = cached
                    _isFetchingLyrics.value = false
                    return@launch
                }
            }

            val synced = try {
                LyricsService.fetchSynced(
                    title = track.title,
                    artist = extractedArtist,
                    duration = trackDuration
                ) { msg -> _lyricsSearchStatus.value = msg }
            } catch (t: Throwable) {
                null
            } finally {
                // Whatever happens, the spinner must clear for this track.
                if (_currentTrack.value?.id == fetchID) _isFetchingLyrics.value = false
            }

            if (_currentTrack.value?.id != fetchID) return@launch
            _lyricsSearchStatus.value = ""
            if (synced != null) {
                _lyrics.value = synced
                LyricsService.saveCached(synced, track.youtubeID)
            }
        }
    }

    fun refreshLyrics() {
        val track = _currentTrack.value ?: return
        _lyrics.value = emptyList()
        _currentLyricIndex.value = 0
        fetchLyrics(track, forceRefresh = true)
    }

    fun resetLyrics(track: Track) {
        LyricsService.deleteCached(track.youtubeID)
        if (_currentTrack.value?.youtubeID == track.youtubeID) {
            _lyrics.value = emptyList()
            _currentLyricIndex.value = 0
            fetchLyrics(track, forceRefresh = true)
        }
    }

    fun editLyricLine(index: Int, newText: String) {
        val track = _currentTrack.value ?: return
        if (newText.isBlank()) return
        val updated = LyricsService.editLine(_lyrics.value, index, newText.trim())
        _lyrics.value = updated
        LyricsService.saveCached(updated, track.youtubeID)
    }

    private fun updateLyricIndex() {
        val lines = _lyrics.value
        if (lines.isEmpty()) return
        val t = _currentTime.value
        var computed = lines.indexOfLast { it.time <= t }
        if (computed < 0) computed = 0

        val holdExpired = System.currentTimeMillis() >= lyricSeekHoldUntil
        val holdIdx = lyricSeekHoldIndex
        // While the tap hold is live, keep the tapped line unless playback has
        // clearly moved two or more lines ahead.
        if (!holdExpired && holdIdx != null) {
            computed = if (computed <= holdIdx + 1) holdIdx else {
                lyricSeekHoldIndex = null
                computed
            }
        }
        if (holdExpired) lyricSeekHoldIndex = null

        if (computed != _currentLyricIndex.value) {
            _currentLyricIndex.value = computed
        }
    }

    /** Line pacing class: compares this line's chars-per-second to neighbours. */
    data class LyricPrediction(val cls: String, val cps: Double, val urgency: Double)

    private fun classifyLyricLine(idx: Int, lyrics: List<LyricLine>): LyricPrediction {
        val line = lyrics[idx]
        val nextLine = if (idx + 1 < lyrics.size) lyrics[idx + 1] else null
        val prevLine = if (idx - 1 >= 0) lyrics[idx - 1] else null

        val chars = max(line.text.replace(" ", "").length, 1)
        val timeSlot = nextLine?.let { it.time - line.time } ?: 4.0
        val cps = chars.toDouble() / max(timeSlot, 0.1)

        val samples = mutableListOf<Double>()
        val lo = max(0, idx - 4)
        val hi = min(lyrics.size - 1, idx + 4)
        for (i in lo..hi) {
            val l = lyrics[i]
            if (l.text.isEmpty()) continue
            val n = if (i + 1 < lyrics.size) lyrics[i + 1] else null
            val c = max(l.text.replace(" ", "").length, 1)
            val t = n?.let { it.time - l.time } ?: 4.0
            samples.add(c.toDouble() / max(t, 0.1))
        }
        val avgCps = if (samples.isEmpty()) cps else samples.sum() / samples.size
        val ratio = cps / max(avgCps, 0.5)
        val silenceBefore = prevLine?.let { line.time - (it.time + 0.2) } ?: 0.0
        val dramaBoost = if (silenceBefore > 0.8) 0.3 else 0.0

        return when {
            ratio > 1.55 -> LyricPrediction("fast", cps, min(1.0, (ratio - 1.55) / 0.9 + 0.65))
            ratio < 0.60 || dramaBoost > 0 -> LyricPrediction("slow", cps, max(0.0, ratio * 0.4 - dramaBoost))
            else -> LyricPrediction("normal", cps, 0.3 + (ratio - 0.6) * 0.7)
        }
    }

    /**
     * Fraction (0..1) of line `idx` that should be bright at `now`. Pure O(1)
     * eased curve: no per-frame simulation, no punctuation holds, no beat
     * term. Those three each caused a visible zap or stutter on iOS and are
     * deliberately left out here too.
     */
    fun revealedFraction(index: Int, now: Double): Double {
        val lyrics = _lyrics.value
        if (index < 0 || index >= lyrics.size) return 0.0
        val line = lyrics[index]
        if (line.text.isEmpty()) return 0.0

        val pred = classifyLyricLine(index, lyrics)
        val lineStart = line.time
        val lineEnd = if (index + 1 < lyrics.size) lyrics[index + 1].time else lineStart + 4
        val lineDur = max(0.15, lineEnd - lineStart)
        val lookahead = when (pred.cls) {
            "fast" -> 0.40
            "slow" -> 0.22
            else -> 0.30
        }

        val rawT = min(1.0, max(0.0, (now - lineStart + lookahead) / lineDur))
        val eased = when (pred.cls) {
            "fast" -> if (rawT < 0.35) (rawT / 0.35) * 0.85 else 0.85 + ((rawT - 0.35) / 0.65) * 0.15
            "slow" -> if (rawT < 0.55) (rawT / 0.55).pow(1.4) * 0.65 else 0.65 + ((rawT - 0.55) / 0.45) * 0.35
            else -> if (rawT < 0.5) 2.4 * rawT * rawT else 1 - (-2 * rawT + 2).pow(2) / 2.4
        }
        return min(1.0, max(0.0, eased))
    }

    /** True when line `idx` is current at time `t`, computed per frame. */
    fun isLineCurrent(index: Int, t: Double): Boolean {
        val lyrics = _lyrics.value
        if (index < 0 || index >= lyrics.size) return false
        if (t < lyrics[index].time) return false
        if (index + 1 < lyrics.size && t >= lyrics[index + 1].time) return false
        return true
    }

}
