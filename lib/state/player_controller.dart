import 'dart:async';
import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart' as ja;
import 'package:just_audio_background/just_audio_background.dart';
import 'package:path_provider/path_provider.dart';

import '../models/lyric_line.dart';
import '../models/track.dart';
import '../services/download_service.dart';
import '../services/library_manager.dart';
import '../services/lyrics_service.dart';
import '../services/thumbnail_cache.dart';
import '../theme/app_theme.dart';

enum LoopMode { none, one, all }

/// Central playback state. Owns the just_audio player, the queue, the lyric
/// index, and the artwork-derived theme. Direct port of the iOS
/// PlayerViewModel, adapted to a single AudioPlayer instance.
class PlayerController extends ChangeNotifier {
  static final PlayerController instance = PlayerController._();
  PlayerController._();

  final ja.AudioPlayer _player = ja.AudioPlayer();

  Track? currentTrack;
  bool isPlaying = false;
  double currentTime = 0;
  double duration = 0;
  bool isSeeking = false;
  bool isLoadingStream = false;
  LoopMode loopMode = LoopMode.none;
  String currentLyricLine = '';
  List<LyricLine> lyrics = [];
  int currentLyricIndex = 0;
  bool isFetchingLyrics = false;
  bool isDownloading = false;
  double downloadProgress = 0;
  String lyricsSearchStatus = '';
  AppTheme theme = AppTheme.dark;
  String? libraryScrollID;
  bool isShuffled = false;

  List<Track> queue = [];
  int queueIndex = 0;

  StreamSubscription<Duration>? _positionSub;
  StreamSubscription<Duration?>? _durationSub;
  StreamSubscription<ja.PlayerState>? _stateSub;
  StreamSubscription<ja.ProcessingState>? _processingSub;

  bool _hasRecordedPlayForCurrent = false;

  int? _lyricSeekHoldIndex;
  DateTime _lyricSeekHoldUntil = DateTime.fromMillisecondsSinceEpoch(0);

  /// High-frequency values live in their own notifiers. The position stream
  /// ticks many times a second; routing that through ChangeNotifier rebuilt
  /// every AnimatedBuilder(PlayerController) in the tree, including every
  /// library row. Only widgets that actually show time listen to these.
  final ValueNotifier<double> timeNotifier = ValueNotifier<double>(0);
  final ValueNotifier<double> durationNotifier = ValueNotifier<double>(0);
  final ValueNotifier<int> lyricIndexNotifier = ValueNotifier<int>(0);

  /// 1/30s sampler. Same cadence the iOS app uses for its time observer, so a
  /// line switches on the boundary instead of up to a stream-tick late.
  Timer? _ticker;

  /// How the lyrics view renders. Karaoke sweeps a bright fill across the
  /// current line; plain is the big-current-line list.
  LyricsMode lyricsMode = LyricsMode.load();

  void setLyricsMode(LyricsMode mode) {
    lyricsMode = mode;
    mode.save();
    notifyListeners();
  }

  Future<void> init() async {
    _processingSub = _player.processingStateStream.listen((state) {
      if (state == ja.ProcessingState.completed) {
        _handleSongEnded();
      }
    });
    _stateSub = _player.playerStateStream.listen((state) {
      isPlaying =
          state.playing && state.processingState != ja.ProcessingState.completed;
      notifyListeners();
    });
  }

  List<Track> get currentQueue => queue;
  int get currentQueueIndex => queueIndex;
  bool get hasQueue => queue.isNotEmpty;
  bool get canPlayNext =>
      queue.length > 1 || (queue.isNotEmpty && queueIndex < queue.length - 1);
  bool get canPlayPrevious =>
      queue.length > 1 || (queue.isNotEmpty && queueIndex > 0);

  void _recordCurrentPlay() {
    final track = currentTrack;
    if (track == null || _hasRecordedPlayForCurrent) return;
    _hasRecordedPlayForCurrent = true;
    LibraryManager.instance.recordPlay(track);
  }

  Future<void> play(Track track) async {
    if (currentTrack?.id == track.id) {
      await togglePlayPause();
      return;
    }

    currentTrack = track;
    theme = AppTheme.dark;
    currentTime = 0;
    duration = 0;
    lyrics = [];
    currentLyricLine = '';
    currentLyricIndex = 0;
    isFetchingLyrics = false;
    isDownloading = false;
    downloadProgress = 0;
    _hasRecordedPlayForCurrent = false;
    _lyricSeekHoldIndex = null;
    _lyricSeekHoldUntil = DateTime.fromMillisecondsSinceEpoch(0);
    timeNotifier.value = 0;
    durationNotifier.value = 0;
    lyricIndexNotifier.value = 0;
    notifyListeners();

    final local = await track.localFile();
    if (local != null && local.existsSync()) {
      await _playFile(local, track);
      return;
    }

    final docs = await getApplicationDocumentsDirectory();
    for (final existing in LibraryManager.instance.tracks) {
      if (existing.youtubeID == track.youtubeID && existing.fileName != null) {
        final file = existing.localFileSync(docs.path);
        if (file != null && file.existsSync()) {
          currentTrack = existing;
          await _playFile(file, existing);
          return;
        }
      }
    }

    isLoadingStream = true;
    isDownloading = true;
    downloadProgress = 0;
    notifyListeners();
    try {
      final saved = await DownloadService.instance.fetchAndSave(
        videoID: track.youtubeID,
        title: track.title,
        thumbnailURL: track.thumbnailURL,
        artist: track.artist,
        onProgress: (p) {
          downloadProgress = p;
          notifyListeners();
        },
      );
      currentTrack = saved;
      isLoadingStream = false;
      isDownloading = false;
      downloadProgress = 0;
      notifyListeners();
      final file = await saved.localFile();
      if (file != null) await _playFile(file, saved);
    } catch (e) {
      debugPrint('[player] download/play failed: $e');
      isLoadingStream = false;
      isDownloading = false;
      downloadProgress = 0;
      notifyListeners();
    }
  }

  Future<void> _playFile(File file, Track track) async {
    try {
      // Attach a MediaItem tag so just_audio_background can build the media
      // notification and lock-screen controls.
      final source = ja.AudioSource.uri(
        Uri.file(file.path),
        tag: MediaItem(
          id: track.youtubeID,
          title: titleDisplay(track),
          artist: artistDisplay(track),
          artUri: track.thumbnailURL != null
              ? Uri.tryParse(track.thumbnailURL!)
              : null,
        ),
      );
      final dur = await _player.setAudioSource(source);
      if (dur != null && dur.inMilliseconds > 0) {
        duration = dur.inMilliseconds / 1000.0;
      }
      await _player.setLoopMode(
          loopMode == LoopMode.one ? ja.LoopMode.one : ja.LoopMode.off);
      await _player.play();
      isPlaying = true;
      _recordCurrentPlay();
      _attachListeners();
      notifyListeners();

      ThumbnailCache.instance.preload(track.youtubeID, track.thumbnailURL);
      unawaited(_refreshTheme(track));
    } catch (e) {
      isPlaying = false;
      notifyListeners();
    }
  }

  void _attachListeners() {
    _positionSub?.cancel();
    _durationSub?.cancel();

    _durationSub = _player.durationStream.listen((d) {
      if (d != null && d.inMilliseconds > 0) {
        duration = d.inMilliseconds / 1000.0;
        durationNotifier.value = duration;
      }
    });

    // Sample the live player position at 1/30s, the same cadence the iOS app's
    // time observer uses. The old code rebuilt the entire AnimatedBuilder tree
    // on every position-stream tick; now time and lyric-index changes only
    // touch their own ValueNotifiers, so the rest of the app stays still.
    _ticker?.cancel();
    _ticker = Timer.periodic(const Duration(milliseconds: 33), (_) {
      if (isSeeking) return;
      final pos = _player.position;
      final t = pos.inMilliseconds / 1000.0;
      currentTime = t;
      timeNotifier.value = t;

      final d = _player.duration;
      if (d != null &&
          d.inMilliseconds > 0 &&
          (d.inMilliseconds / 1000.0) != duration) {
        duration = d.inMilliseconds / 1000.0;
        durationNotifier.value = duration;
      }

      _updateLyricLine();
    });
  }

  void _handleSongEnded() {
    switch (loopMode) {
      case LoopMode.one:
        _player.seek(Duration.zero);
        _player.play();
        break;
      case LoopMode.all:
        nextTrack();
        break;
      case LoopMode.none:
        stop();
        break;
    }
  }

  Future<void> togglePlayPause() async {
    if (_player.playing) {
      await _player.pause();
      isPlaying = false;
    } else {
      await _player.play();
      isPlaying = true;
    }
    notifyListeners();
  }

  Future<void> seek(double time) async {
    final candidate = duration > 0 ? duration : (currentTrack?.duration ?? time);
    final effectiveMax = candidate.isFinite && candidate > 0 ? candidate : time;
    final clamped =
        time.isFinite ? time.clamp(0, effectiveMax).toDouble() : 0.0;
    await _player.seek(Duration(milliseconds: (clamped * 1000).round()));
    currentTime = clamped;
    timeNotifier.value = clamped;
    _updateLyricLine();
  }

  Future<void> skipForward() => seek(currentTime + 15);
  Future<void> skipBackward() => seek(currentTime - 15);

  /// Jump playback to a tapped lyric and immediately sync the lyric index.
  Future<void> seekToLyric(LyricLine line) async {
    final candidate =
        duration > 0 ? duration : (currentTrack?.duration ?? line.startTime);
    final effectiveMax =
        candidate.isFinite && candidate > 0 ? candidate : line.startTime;
    final target = line.startTime.isFinite
        ? line.startTime.clamp(0, effectiveMax).toDouble()
        : 0.0;

    final idx = lyrics.indexWhere((l) => l.id == line.id);
    if (idx >= 0) {
      currentLyricIndex = idx;
      currentLyricLine = line.text;
      lyricIndexNotifier.value = idx;
      _lyricSeekHoldIndex = idx;
      _lyricSeekHoldUntil = DateTime.now().add(const Duration(seconds: 1));
    }

    isSeeking = true;
    notifyListeners();
    await _player.seek(Duration(milliseconds: (target * 1000).round()));
    currentTime = _player.position.inMilliseconds / 1000.0;
    timeNotifier.value = currentTime;
    isSeeking = false;
    _updateLyricLine();
  }

  void toggleLoop() {
    switch (loopMode) {
      case LoopMode.none:
        loopMode = LoopMode.one;
        break;
      case LoopMode.one:
        loopMode = LoopMode.all;
        break;
      case LoopMode.all:
        loopMode = LoopMode.none;
        break;
    }
    _player.setLoopMode(
        loopMode == LoopMode.one ? ja.LoopMode.one : ja.LoopMode.off);
    notifyListeners();
  }

  void nextTrack() {
    if (queue.isEmpty) {
      _player.seek(Duration.zero);
      _player.play();
      return;
    }
    if (queueIndex < queue.length - 1) {
      queueIndex += 1;
    } else {
      queueIndex = 0;
    }
    _playFromQueue(queue[queueIndex]);
  }

  void previousTrack() {
    if (queue.isEmpty) return;
    if (queueIndex > 0) {
      queueIndex -= 1;
    } else {
      queueIndex = queue.length - 1;
    }
    _playFromQueue(queue[queueIndex]);
  }

  void _playFromQueue(Track track) {
    if (currentTrack?.id == track.id) {
      _player.seek(Duration.zero);
      _player.play();
      isPlaying = true;
      notifyListeners();
      return;
    }
    play(track);
  }

  void setQueue(List<Track> tracks, {int startIndex = 0}) {
    queue = List<Track>.from(tracks);
    queueIndex = tracks.isEmpty ? 0 : startIndex.clamp(0, tracks.length - 1);
    isShuffled = false;
    notifyListeners();
  }

  void shuffleQueue() {
    if (queue.length <= 1) return;
    final current = queueIndex;
    final rest = List<Track>.from(queue)..removeAt(current);
    rest.shuffle();
    queue = [queue[current], ...rest];
    queueIndex = 0;
    isShuffled = true;
    notifyListeners();
  }

  void playNext(Track track) {
    if (queue.isEmpty) {
      setQueue([track]);
      play(track);
      return;
    }
    queue.insert(queueIndex + 1, track);
    notifyListeners();
  }

  void addToQueue(Track track) {
    queue.add(track);
    notifyListeners();
  }

  void playTrackAt(int index) {
    if (index < 0 || index >= queue.length) return;
    queueIndex = index;
    play(queue[queueIndex]);
  }

  Future<void> stop() async {
    await _player.stop();
    currentTrack = null;
    isPlaying = false;
    currentTime = 0;
    duration = 0;
    isLoadingStream = false;
    loopMode = LoopMode.none;
    theme = AppTheme.dark;
    currentLyricLine = '';
    isFetchingLyrics = false;
    isDownloading = false;
    downloadProgress = 0;
    _hasRecordedPlayForCurrent = false;
    _lyricSeekHoldIndex = null;
    _lyricSeekHoldUntil = DateTime.fromMillisecondsSinceEpoch(0);
    queue = [];
    queueIndex = 0;
    timeNotifier.value = 0;
    durationNotifier.value = 0;
    lyricIndexNotifier.value = 0;
    notifyListeners();
  }

  // MARK: - Lyrics

  void fetchLyricsForCurrentTrack({bool forceRefresh = false}) {
    _fetchLyrics(forceRefresh);
  }

  void refreshLyrics() => _fetchLyrics(true);

  void resetLyrics(Track track) {
    LyricsService.deleteCachedLyrics(track.youtubeID);
    if (currentTrack?.youtubeID == track.youtubeID) {
      lyrics = [];
      currentLyricLine = '';
      currentLyricIndex = 0;
      refreshLyrics();
    }
  }

  void editLyricLine(int index, String newText) {
    final text = newText.trim();
    if (currentTrack == null || text.isEmpty) return;
    if (index < 0 || index >= lyrics.length) return;

    final updated = LyricsService.syncEditLyricLine(text, index, lyrics);
    lyrics = updated;
    if (index == currentLyricIndex) {
      currentLyricLine = updated[index].text;
    }
    final track = currentTrack;
    if (track != null) {
      LyricsService.saveCachedLyricsSync(updated, track.youtubeID);
    }
    notifyListeners();
  }

  Future<void> _fetchLyrics(bool forceRefresh) async {
    final track = currentTrack;
    if (track == null) return;
    final fetchID = track.id;

    isFetchingLyrics = true;
    notifyListeners();

    final rawTitle = track.title;
    final trackDuration = track.duration ?? 180;

    String? extractedArtist = track.artist;
    if (extractedArtist == null || extractedArtist.isEmpty) {
      final parts = rawTitle.split(' - ');
      if (parts.length >= 2) extractedArtist = parts[0].trim();
    }

    if (!forceRefresh) {
      final cached = await LyricsService.loadCachedLyrics(track.youtubeID);
      if (currentTrack?.id != fetchID) return;
      if (cached != null) {
        lyrics = cached;
        isFetchingLyrics = false;
        notifyListeners();
        return;
      }
    }

    List<LyricLine>? synced;
    try {
      synced = await LyricsService.fetchSynced(
        title: rawTitle,
        artist: extractedArtist,
        duration: trackDuration,
        onStatus: (msg) {
          lyricsSearchStatus = msg;
          notifyListeners();
        },
      );
    } catch (_) {
      synced = null;
    }

    if (currentTrack?.id != fetchID) return;

    lyricsSearchStatus = '';
    if (synced != null) {
      lyrics = synced;
      await LyricsService.saveCachedLyrics(synced, track.youtubeID);
    }
    isFetchingLyrics = false;
    notifyListeners();
  }

  void _updateLyricLine() {
    if (lyrics.isEmpty) return;

    int? computed;
    for (var i = lyrics.length - 1; i >= 0; i--) {
      if (lyrics[i].time <= currentTime) {
        computed = i;
        break;
      }
    }

    final holdExpired = DateTime.now().isAfter(_lyricSeekHoldUntil);

    if (!holdExpired && _lyricSeekHoldIndex != null && computed != null) {
      final holdIdx = _lyricSeekHoldIndex!;
      if (computed <= holdIdx + 1) {
        computed = holdIdx;
      } else {
        _lyricSeekHoldIndex = null;
      }
    }

    if (holdExpired) _lyricSeekHoldIndex = null;

    if (computed != null &&
        (computed != currentLyricIndex || currentLyricLine.isEmpty)) {
      currentLyricIndex = computed;
      currentLyricLine = lyrics[computed].text;
      // High-frequency: only the notifier, never notifyListeners(). The lyrics
      // view listens to this directly, so a line change never rebuilds the
      // rest of the app.
      lyricIndexNotifier.value = computed;
    }
  }

  Future<void> _refreshTheme(Track track) async {
    final targetID = track.id;
    final remote = track.thumbnailURL;

    if (remote != null) {
      try {
        // Reuse the disk-cached thumbnail instead of a second network fetch,
        // then run the color extraction off the main isolate.
        final file = await ThumbnailCache.instance
            .ensureCached(track.youtubeID, remote);
        if (file != null) {
          final bytes = await file.readAsBytes();
          if (currentTrack?.id == targetID) {
            final newTheme =
                await compute(AppTheme.fromBytesSync, bytes);
            if (currentTrack?.id == targetID) {
              theme = newTheme;
              notifyListeners();
            }
            return;
          }
        }
      } catch (_) {}
    }

    if (currentTrack?.id == targetID) {
      theme = AppTheme.dark;
      notifyListeners();
    }
  }

  String titleDisplay(Track track) {
    final parts = track.title.split(' - ');
    final raw = parts.length >= 2 ? parts.sublist(1).join(' - ') : track.title;
    return raw.replaceAll(RegExp(r'\(.*?\)'), '').trim();
  }

  String artistDisplay(Track track) {
    final artist = track.artist;
    if (artist != null && artist.isNotEmpty) return artist;
    final parts = track.title.split(' - ');
    if (parts.length < 2) return 'Unknown Artist';
    return parts[0].replaceAll(RegExp(r'\(.*?\)'), '').trim();
  }

  @override
  void dispose() {
    _ticker?.cancel();
    _positionSub?.cancel();
    _durationSub?.cancel();
    _stateSub?.cancel();
    _processingSub?.cancel();
    timeNotifier.dispose();
    durationNotifier.dispose();
    lyricIndexNotifier.dispose();
    _player.dispose();
    super.dispose();
  }
}

/// How the lyrics view renders. Persisted to a one-line file in Documents so
/// the choice survives a restart. main() calls LyricsMode.init() before runApp.
enum LyricsMode {
  karaoke,
  plain;

  static File? _file;
  static String _key = 'karaoke';

  static Future<void> init(String docsPath) async {
    _file = File('$docsPath/lyrics_mode.txt');
    try {
      if (_file!.existsSync()) {
        final raw = _file!.readAsStringSync().trim();
        if (raw.isNotEmpty) _key = raw;
      }
    } catch (_) {}
  }

  static LyricsMode load() =>
      _key == 'plain' ? LyricsMode.plain : LyricsMode.karaoke;

  void save() {
    _key = name;
    try {
      _file?.writeAsStringSync(name, flush: true);
    } catch (_) {}
  }
}

/// Pacing + reveal math for the karaoke sweep. Ported from the iOS
/// PlayerViewModel (karaokeFraction / revealedFraction / classifyLyricLine).
/// Pure functions so the lyrics widget can call them per frame without
/// rebuilding anything.
class LyricMath {
  static String classify(List<LyricLine> lyrics, int idx) {
    if (idx < 0 || idx >= lyrics.length) return 'normal';
    final line = lyrics[idx];
    final nextLine = idx + 1 < lyrics.length ? lyrics[idx + 1] : null;
    final prevLine = idx - 1 >= 0 ? lyrics[idx - 1] : null;

    final stripped = line.text.replaceAll(' ', '');
    final chars = stripped.isEmpty ? 1 : stripped.length;
    final timeSlot = nextLine != null ? nextLine.time - line.time : 4.0;
    final cps = chars / (timeSlot > 0.1 ? timeSlot : 0.1);

    final samples = <double>[];
    final lo = (idx - 4) < 0 ? 0 : idx - 4;
    final hi = (idx + 4) > lyrics.length - 1 ? lyrics.length - 1 : idx + 4;
    for (var i = lo; i <= hi; i++) {
      final l = lyrics[i];
      if (l.text.isEmpty) continue;
      final n = i + 1 < lyrics.length ? lyrics[i + 1] : null;
      final strippedLine = l.text.replaceAll(' ', '');
      final c = strippedLine.isEmpty ? 1 : strippedLine.length;
      final t = n != null ? n.time - l.time : 4.0;
      samples.add(c / (t > 0.1 ? t : 0.1));
    }
    final avg = samples.isEmpty
        ? cps
        : samples.reduce((a, b) => a + b) / samples.length;
    final ratio = cps / (avg < 0.5 ? 0.5 : avg);
    final silenceBefore =
        prevLine != null ? line.time - (prevLine.time + 0.2) : 0.0;
    final dramaBoost = silenceBefore > 0.8 ? 0.3 : 0.0;

    if (ratio > 1.55) return 'fast';
    if (ratio < 0.60 || dramaBoost > 0) return 'slow';
    return 'normal';
  }

  /// Fraction (0...1) of the current line that should be bright at time `now`.
  /// A single smooth monotonic curve, same as the fixed iOS version.
  static double revealedFraction(
      List<LyricLine> lyrics, int idx, double now) {
    if (idx < 0 || idx >= lyrics.length) return 0;
    final line = lyrics[idx];
    final totalChars = line.text.length;
    if (totalChars <= 0) return 0;

    final cls = classify(lyrics, idx);
    final lineStart = line.time;
    final lineEnd = idx + 1 < lyrics.length
        ? lyrics[idx + 1].time
        : lineStart + 4;
    final lineDur = (lineEnd - lineStart) < 0.15 ? 0.15 : (lineEnd - lineStart);
    final lookahead = cls == 'fast' ? 0.40 : (cls == 'slow' ? 0.22 : 0.30);
    final rawT = ((now - lineStart + lookahead) / lineDur).clamp(0.0, 1.0);

    double eased;
    if (cls == 'fast') {
      eased = rawT < 0.35
          ? (rawT / 0.35) * 0.85
          : 0.85 + ((rawT - 0.35) / 0.65) * 0.15;
    } else if (cls == 'slow') {
      eased = rawT < 0.55
          ? math.pow(rawT / 0.55, 1.4).toDouble() * 0.65
          : 0.65 + ((rawT - 0.55) / 0.45) * 0.35;
    } else {
      eased = rawT < 0.5
          ? 2.4 * rawT * rawT
          : 1 - math.pow(-2 * rawT + 2, 2).toDouble() / 2.4;
    }
    return eased.clamp(0.0, 1.0);
  }

  /// Whether line `idx` is the current line at time `t`, computed from live
  /// time so the highlight switches exactly on the boundary.
  static bool isLineCurrent(List<LyricLine> lyrics, int idx, double t) {
    if (idx < 0 || idx >= lyrics.length) return false;
    if (t < lyrics[idx].time) return false;
    if (idx + 1 < lyrics.length && t >= lyrics[idx + 1].time) return false;
    return true;
  }
}