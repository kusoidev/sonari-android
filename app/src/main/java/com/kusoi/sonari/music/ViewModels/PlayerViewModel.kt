package com.kusoi.sonari.music.ViewModels

import android.graphics.BitmapFactory
import com.kusoi.sonari.music.Models.AppTheme
import com.kusoi.sonari.music.Models.Track
import com.kusoi.sonari.music.Services.DownloadService
import com.kusoi.sonari.music.Services.LibraryManager
import com.kusoi.sonari.music.Services.LyricLine
import com.kusoi.sonari.music.Services.LyricsService
import com.kusoi.sonari.music.Services.PlayerHolder
import com.kusoi.sonari.music.Services.ThumbnailCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL
import java.util.Date

enum class LoopMode { NONE, ONE, ALL }

/**
 * Player state holder. Mirrors the Swift PlayerViewModel: queue management, transport,
 * loop modes, lyric sync with the seek-hold guard, theme refresh, and play counting.
 */
object PlayerViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTime = MutableStateFlow(0.0)
    val currentTime: StateFlow<Double> = _currentTime.asStateFlow()

    private val _duration = MutableStateFlow(0.0)
    val duration: StateFlow<Double> = _duration.asStateFlow()

    private val _isLoadingStream = MutableStateFlow(false)
    val isLoadingStream: StateFlow<Boolean> = _isLoadingStream.asStateFlow()

    private val _loopMode = MutableStateFlow(LoopMode.NONE)
    val loopMode: StateFlow<LoopMode> = _loopMode.asStateFlow()

    private val _currentLyricLine = MutableStateFlow("")
    val currentLyricLine: StateFlow<String> = _currentLyricLine.asStateFlow()

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

    private val _theme = MutableStateFlow(AppTheme.dark)
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private var tickJob: Job? = null
    private var lyricsJob: Job? = null
    private var hasRecordedPlayForCurrent = false

    /** After a lyric tap the tapped index is authoritative for one second. */
    private var lyricSeekHoldIndex: Int? = null
    private var lyricSeekHoldUntil: Long = 0

    val hasQueue: Boolean get() = _queue.value.isNotEmpty()

    private fun recordCurrentPlay() {
        val track = _currentTrack.value ?: return
        if (hasRecordedPlayForCurrent) return
        hasRecordedPlayForCurrent = true
        LibraryManager.recordPlay(track)
    }

    fun play(track: Track) {
        if (_currentTrack.value?.id == track.id) {
            togglePlayPause()
            return
        }

        _currentTrack.value = track
        _theme.value = AppTheme.dark
        _currentTime.value = 0.0
        _duration.value = 0.0
        _lyrics.value = emptyList()
        _currentLyricLine.value = ""
        _currentLyricIndex.value = 0
        _isFetchingLyrics.value = false
        _isDownloading.value = false
        _downloadProgress.value = 0.0
        hasRecordedPlayForCurrent = false
        lyricSeekHoldIndex = null
        lyricSeekHoldUntil = 0
        lyricsJob?.cancel()

        val local = track.localFileURL
        if (local != null && local.exists()) {
            playFile(local)
            return
        }

        val existing = LibraryManager.tracks.value.firstOrNull {
            it.youtubeID == track.youtubeID && it.fileName != null
        }
        val existingFile = existing?.localFileURL
        if (existing != null && existingFile != null && existingFile.exists()) {
            _currentTrack.value = existing
            playFile(existingFile)
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
                    artist = track.artist,
                    onProgress = { p -> _downloadProgress.value = p }
                )
                _currentTrack.value = saved
                _isLoadingStream.value = false
                _isDownloading.value = false
                _downloadProgress.value = 0.0
                saved.localFileURL?.let { playFile(it) }
            }.onFailure {
                _isLoadingStream.value = false
                _isDownloading.value = false
                _downloadProgress.value = 0.0
            }
        }
    }

    private fun playFile(file: File) {
        val player = PlayerHolder.player ?: return
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(file.toURI().toString()))
        player.prepare()
        player.play()
        _isPlaying.value = true
        recordCurrentPlay()

        startTick()
        startThemeRefresh()
        _currentTrack.value?.let { track ->
            scope.launch { ThumbnailCache.preload(track.youtubeID, track.thumbnailURL) }
        }
    }

    private fun startTick() {
        tickJob?.cancel()
        tickJob = scope.launch {
            val player = PlayerHolder.player
            while (true) {
                delay(500)
                val p = PlayerHolder.player ?: continue
                val pos = p.currentPosition / 1000.0
                if (pos.isFinite()) _currentTime.value = pos

                val dur = p.duration
                if (dur > 0) _duration.value = dur / 1000.0

                updateLyricLine()

                if (p.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    handleSongEnded()
                }
            }
        }
    }

    private fun handleSongEnded() {
        when (_loopMode.value) {
            LoopMode.ONE -> {
                PlayerHolder.player?.seekTo(0)
                PlayerHolder.player?.play()
            }
            LoopMode.ALL -> nextTrack()
            LoopMode.NONE -> stop()
        }
    }

    fun togglePlayPause() {
        val player = PlayerHolder.player ?: return
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
        } else {
            player.play()
            _isPlaying.value = true
        }
    }

    fun seek(to: Double) {
        val player = PlayerHolder.player ?: return
        val max = if (_duration.value > 0) _duration.value else (_currentTrack.value?.duration ?: to)
        val clamped = to.coerceIn(0.0, if (max > 0) max else to)
        player.seekTo((clamped * 1000).toLong())
        _currentTime.value = clamped
    }

    fun skipForward() { seek(_currentTime.value + 15) }
    fun skipBackward() { seek((_currentTime.value - 15).coerceAtLeast(0.0)) }

    fun seekToLyric(line: LyricLine) {
        val idx = _lyrics.value.indexOfFirst { it.id == line.id }
        if (idx >= 0) {
            _currentLyricIndex.value = idx
            _currentLyricLine.value = line.text
            lyricSeekHoldIndex = idx
            lyricSeekHoldUntil = System.currentTimeMillis() + 1000
        }
        seek(line.startTime)
    }

    fun toggleLoop() {
        _loopMode.value = when (_loopMode.value) {
            LoopMode.NONE -> LoopMode.ONE
            LoopMode.ONE -> LoopMode.ALL
            LoopMode.ALL -> LoopMode.NONE
        }
    }

    fun nextTrack() {
        val q = _queue.value
        if (q.isEmpty()) {
            PlayerHolder.player?.seekTo(0)
            PlayerHolder.player?.play()
            return
        }
        _queueIndex.value = if (_queueIndex.value < q.size - 1) _queueIndex.value + 1 else 0
        playFromQueue(q[_queueIndex.value])
    }

    fun previousTrack() {
        val q = _queue.value
        if (q.isEmpty()) return
        _queueIndex.value = if (_queueIndex.value > 0) _queueIndex.value - 1 else q.size - 1
        playFromQueue(q[_queueIndex.value])
    }

    private fun playFromQueue(track: Track) {
        if (_currentTrack.value?.id == track.id) {
            PlayerHolder.player?.seekTo(0)
            PlayerHolder.player?.play()
            _isPlaying.value = true
            return
        }
        play(track)
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        _queue.value = tracks
        _queueIndex.value = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        _isShuffled.value = false
    }

    fun shuffleQueue() {
        val q = _queue.value
        if (q.size <= 1) return
        val current = _queueIndex.value
        val rest = q.filterIndexed { i, _ -> i != current }.shuffled()
        _queue.value = listOf(q[current]) + rest
        _queueIndex.value = 0
        _isShuffled.value = true
    }

    fun playNext(track: Track) {
        val q = _queue.value
        if (q.isEmpty()) {
            setQueue(listOf(track), 0)
            play(track)
            return
        }
        val mutable = q.toMutableList()
        mutable.add(_queueIndex.value + 1, track)
        _queue.value = mutable
    }

    fun addToQueue(track: Track) {
        _queue.value = _queue.value + track
    }

    fun playTrack(at: Int) {
        val q = _queue.value
        if (at < 0 || at >= q.size) return
        _queueIndex.value = at
        play(q[at])
    }

    fun stop() {
        PlayerHolder.player?.stop()
        _currentTrack.value = null
        _isPlaying.value = false
        _currentTime.value = 0.0
        _duration.value = 0.0
        _isLoadingStream.value = false
        _loopMode.value = LoopMode.NONE
        _theme.value = AppTheme.dark
        _currentLyricLine.value = ""
        _isFetchingLyrics.value = false
        _lyrics.value = emptyList()
        _queue.value = emptyList()
        _queueIndex.value = 0
        tickJob?.cancel()
        lyricsJob?.cancel()
        hasRecordedPlayForCurrent = false
    }

    fun fetchLyricsForCurrentTrack(forceRefresh: Boolean = false) {
        val track = _currentTrack.value ?: return
        _isFetchingLyrics.value = true
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            val extractedArtist = track.artist?.takeIf { it.isNotEmpty() }
                ?: track.title.split(" - ").takeIf { it.size >= 2 }?.get(0)?.trim()

            if (!forceRefresh) {
                LyricsService.loadCachedLyrics(track.youtubeID)?.let {
                    _lyrics.value = it
                    _isFetchingLyrics.value = false
                    return@launch
                }
            }

            val synced = runCatching {
                LyricsService.fetchSynced(
                    title = track.title,
                    artist = extractedArtist,
                    duration = track.duration ?: 180.0,
                    onStatus = { msg -> _lyricsSearchStatus.value = msg }
                )
            }.getOrNull()

            _lyricsSearchStatus.value = ""
            _isFetchingLyrics.value = false
            if (synced != null) {
                _lyrics.value = synced
                LyricsService.saveCachedLyrics(synced, track.youtubeID)
            }
        }
    }

    fun refreshLyrics() {
        _currentTrack.value?.let { LyricsService.deleteCachedLyrics(it.youtubeID) }
        fetchLyricsForCurrentTrack(forceRefresh = true)
    }

    fun resetLyrics(track: Track) {
        scope.launch { LyricsService.deleteCachedLyrics(track.youtubeID) }
        if (_currentTrack.value?.youtubeID == track.youtubeID) {
            _lyrics.value = emptyList()
            _currentLyricLine.value = ""
            _currentLyricIndex.value = 0
            refreshLyrics()
        }
    }

    fun editLyricLine(at: Int, newText: String) {
        val track = _currentTrack.value ?: return
        val trimmed = newText.trim()
        if (trimmed.isEmpty() || at !in _lyrics.value.indices) return
        val updated = LyricsService.syncEditLyricLine(trimmed, at, _lyrics.value)
        _lyrics.value = updated
        if (at == _currentLyricIndex.value) _currentLyricLine.value = updated[at].text
        LyricsService.saveCachedLyricsSync(updated, track.youtubeID)
    }

    private fun updateLyricLine() {
        val lines = _lyrics.value
        if (lines.isEmpty()) return

        var computed = lines.indexOfLast { it.time <= _currentTime.value }
        val holdExpired = System.currentTimeMillis() >= lyricSeekHoldUntil

        if (!holdExpired && lyricSeekHoldIndex != null && computed >= 0) {
            val holdIdx = lyricSeekHoldIndex!!
            if (computed <= holdIdx + 1) computed = holdIdx else lyricSeekHoldIndex = null
        }
        if (holdExpired) lyricSeekHoldIndex = null

        if (computed >= 0 && (computed != _currentLyricIndex.value || _currentLyricLine.value.isEmpty())) {
            _currentLyricIndex.value = computed
            _currentLyricLine.value = lines[computed].text
        }
    }

    private fun startThemeRefresh() {
        val track = _currentTrack.value ?: return
        val targetID = track.id
        scope.launch(Dispatchers.IO) {
            val url = track.thumbnailURL
            val bitmap = if (url != null) {
                runCatching { BitmapFactory.decodeStream(URL(url).openStream()) }.getOrNull()
            } else null
            val newTheme = AppTheme.from(bitmap)
            if (_currentTrack.value?.id == targetID) {
                _theme.value = newTheme
            }
        }
    }

    fun titleDisplay(track: Track): String {
        val parts = track.title.split(" - ")
        return if (parts.size >= 2) parts.drop(1).joinToString(" - ") else track.title
    }

    fun artistDisplay(track: Track): String {
        track.artist?.takeIf { it.isNotEmpty() }?.let { return it }
        val parts = track.title.split(" - ")
        return if (parts.size >= 2) parts[0] else "Unknown Artist"
    }
}