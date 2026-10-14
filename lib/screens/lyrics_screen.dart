import 'package:flutter/material.dart';

import '../models/lyric_line.dart';
import '../models/track.dart';
import '../state/player_controller.dart';
import '../widgets/cached_thumbnail.dart';
import '../widgets/karaoke_text.dart';
import '../widgets/theme_background.dart';

/// Lyrics tab. Port of the iOS LyricsView: a mini player header that expands
/// into full transport controls, then a center-anchored, auto-scrolling lyric
/// list with tap-to-seek, per-line editing, and a karaoke sweep on the current
/// line. The list only rebuilds when the active line changes; the karaoke
/// sweep repaints just the one current line off the 30Hz time notifier.
class LyricsScreen extends StatefulWidget {
  final bool isActive;
  const LyricsScreen({super.key, required this.isActive});

  @override
  State<LyricsScreen> createState() => _LyricsScreenState();
}

class _LyricsScreenState extends State<LyricsScreen> {
  bool _isExpanded = false;
  final ScrollController _scrollController = ScrollController();

  int _lastIndex = -1;
  List<GlobalKey> _lineKeys = [];
  int _keysForLength = -1;

  int? _editingIndex;
  final TextEditingController _editController = TextEditingController();
  final FocusNode _editFocus = FocusNode();

  PlayerController get _player => PlayerController.instance;

  @override
  void initState() {
    super.initState();
    _player.fetchLyricsForCurrentTrack();
    _player.addListener(_onStructuralChange);
    _player.lyricIndexNotifier.addListener(_onIndexChange);
    _lastIndex = _player.currentLyricIndex;
    // The tab is built fresh on entry (only the active tab stays mounted), so
    // jump to the current line with no animation once the list has laid out.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _scrollToCurrent(animate: false);
      WidgetsBinding.instance
          .addPostFrameCallback((_) => _scrollToCurrent(animate: false));
    });
  }

  @override
  void didUpdateWidget(covariant LyricsScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    // The tab is unmounted while offscreen, so re-entering has no prior scroll
    // position. Glide to the current line instead of snapping.
    if (widget.isActive && !oldWidget.isActive) {
      WidgetsBinding.instance
          .addPostFrameCallback((_) => _scrollToCurrent(animate: true));
    }
  }

  @override
  void dispose() {
    _player.removeListener(_onStructuralChange);
    _player.lyricIndexNotifier.removeListener(_onIndexChange);
    _scrollController.dispose();
    _editController.dispose();
    _editFocus.dispose();
    super.dispose();
  }

  void _onStructuralChange() {
    if (mounted) setState(() {});
  }

  void _onIndexChange() {
    final idx = _player.lyricIndexNotifier.value;
    if (idx == _lastIndex) return;
    _lastIndex = idx;
    if (mounted) setState(() {});
    WidgetsBinding.instance
        .addPostFrameCallback((_) => _scrollToCurrent(animate: true));
  }

  /// Scroll so the current line sits at the vertical center. Uses the line key
  /// so the real measured height is honored instead of a fixed row guess.
  void _scrollToCurrent({required bool animate}) {
    final idx = _player.currentLyricIndex;
    if (idx < 0 || idx >= _lineKeys.length) return;
    final ctx = _lineKeys[idx].currentContext;
    if (ctx == null) return;
    Scrollable.ensureVisible(
      ctx,
      alignment: 0.5,
      duration: animate ? const Duration(milliseconds: 520) : Duration.zero,
      curve: Curves.easeOutCubic,
    );
  }

  void _syncKeys(int count) {
    if (_keysForLength == count) return;
    _keysForLength = count;
    _lineKeys = List<GlobalKey>.generate(count, (_) => GlobalKey());
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _player,
      builder: (context, _) {
        final track = _player.currentTrack;
        return Stack(
          children: [
            const Positioned.fill(child: ThemeBackground()),
            SafeArea(
              bottom: false,
              child: track == null
                  ? _noTrackView()
                  : Column(
                      children: [
                        _topMiniPlayer(track),
                        Expanded(child: _body()),
                      ],
                    ),
            ),
          ],
        );
      },
    );
  }

  Widget _body() {
    if (_player.isFetchingLyrics) return _loadingView();
    if (_player.lyrics.isEmpty) return _emptyView();
    return _lyricsList();
  }

  Widget _topMiniPlayer(Track track) {
    return Column(
      children: [
        InkWell(
          onTap: () {
            setState(() => _isExpanded = !_isExpanded);
          },
          child: Padding(
            padding: EdgeInsets.fromLTRB(16, 8, 16, _isExpanded ? 12 : 10),
            child: Row(
              children: [
                CachedThumbnail(
                  youtubeID: track.youtubeID,
                  remoteURL: track.thumbnailURL,
                  width: 36,
                  height: 36,
                  radius: 6,
                  iconSize: 12,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(_player.titleDisplay(track),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: Colors.white)),
                      const SizedBox(height: 1),
                      Text(_player.artistDisplay(track),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                              fontSize: 11,
                              color: Colors.white.withValues(alpha: 0.45))),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: _player.togglePlayPause,
                  icon: Icon(
                      _player.isPlaying ? Icons.pause : Icons.play_arrow,
                      size: 22,
                      color: Colors.white),
                ),
                IconButton(
                  onPressed: _player.nextTrack,
                  icon: Icon(Icons.skip_next,
                      size: 20, color: Colors.white.withValues(alpha: 0.55)),
                ),
                IconButton(
                  onPressed: _showLyricsModeMenu,
                  icon: Icon(Icons.more_horiz,
                      size: 22, color: Colors.white.withValues(alpha: 0.55)),
                ),
              ],
            ),
          ),
        ),
        AnimatedCrossFade(
          duration: const Duration(milliseconds: 320),
          firstCurve: Curves.easeOutCubic,
          secondCurve: Curves.easeOutCubic,
          sizeCurve: Curves.easeOutCubic,
          crossFadeState: _isExpanded
              ? CrossFadeState.showSecond
              : CrossFadeState.showFirst,
          firstChild: _progressBar(track),
          secondChild: _expandedControls(track),
        ),
      ],
    );
  }

  void _showLyricsModeMenu() {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: const Color(0xFF1A1A1E),
      builder: (context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: Icon(Icons.graphic_eq,
                    color: _player.lyricsMode == LyricsMode.karaoke
                        ? _player.theme.accent
                        : Colors.white54),
                title: const Text('Karaoke Highlight',
                    style: TextStyle(color: Colors.white)),
                trailing: _player.lyricsMode == LyricsMode.karaoke
                    ? Icon(Icons.check, color: _player.theme.accent)
                    : null,
                onTap: () {
                  _player.setLyricsMode(LyricsMode.karaoke);
                  Navigator.pop(context);
                },
              ),
              ListTile(
                leading: Icon(Icons.subject,
                    color: _player.lyricsMode == LyricsMode.plain
                        ? _player.theme.accent
                        : Colors.white54),
                title: const Text('Plain',
                    style: TextStyle(color: Colors.white)),
                trailing: _player.lyricsMode == LyricsMode.plain
                    ? Icon(Icons.check, color: _player.theme.accent)
                    : null,
                onTap: () {
                  _player.setLyricsMode(LyricsMode.plain);
                  Navigator.pop(context);
                },
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _progressBar(Track track) {
    final dur = _player.duration > 0 ? _player.duration : (track.duration ?? 0);
    return ValueListenableBuilder<double>(
      valueListenable: _player.timeNotifier,
      builder: (context, t, _) {
        final fraction = dur > 0 ? (t / dur).clamp(0.0, 1.0) : 0.0;
        return SizedBox(
          height: 2,
          child: FractionallySizedBox(
            alignment: Alignment.centerLeft,
            widthFactor: fraction,
            child: Container(color: _player.theme.accent),
          ),
        );
      },
    );
  }

  Widget _expandedControls(Track track) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 14),
      child: Column(
        children: [
          _ExpandedSlider(
              player: _player, fallbackDuration: track.duration ?? 1),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              IconButton(
                onPressed: _player.toggleLoop,
                icon: Icon(
                  _player.loopMode == LoopMode.one
                      ? Icons.repeat_one
                      : Icons.repeat,
                  color: _player.loopMode == LoopMode.none
                      ? Colors.white.withValues(alpha: 0.3)
                      : Colors.white,
                ),
              ),
              IconButton(
                onPressed: _player.previousTrack,
                icon: const Icon(Icons.skip_previous,
                    size: 30, color: Colors.white),
              ),
              IconButton(
                onPressed: _player.togglePlayPause,
                icon: Icon(
                    _player.isPlaying
                        ? Icons.pause_circle_filled
                        : Icons.play_circle_filled,
                    size: 52,
                    color: Colors.white),
              ),
              IconButton(
                onPressed: _player.nextTrack,
                icon: const Icon(Icons.skip_next,
                    size: 30, color: Colors.white),
              ),
              IconButton(
                onPressed: () {
                  setState(() => _isExpanded = false);
                },
                icon: Icon(Icons.close,
                    size: 18, color: Colors.white.withValues(alpha: 0.55)),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _lyricsList() {
    final lyrics = _player.lyrics;
    _syncKeys(lyrics.length);
    return ListView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.symmetric(vertical: 40),
      itemCount: lyrics.length,
      itemBuilder: (context, index) {
        return _lyricLine(lyrics[index], index);
      },
    );
  }

  Widget _lyricLine(LyricLine line, int index) {
    final offset = index - _player.currentLyricIndex;
    final isCurrent = offset == 0;
    final isNear = offset.abs() <= 1;
    final isFar = offset.abs() >= 3;
    final isEditing = _editingIndex == index;

    final fontSize = isCurrent ? 26.0 : (isNear ? 20.0 : 17.0);
    final weight = isCurrent
        ? FontWeight.bold
        : (isNear ? FontWeight.w600 : FontWeight.w500);
    final color = isCurrent
        ? Colors.white
        : (isFar
            ? Colors.white.withValues(alpha: 0.2)
            : Colors.white.withValues(alpha: 0.4));

    if (isEditing) {
      return Padding(
        key: _lineKeys[index],
        padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 8),
        child: TextField(
          controller: _editController,
          focusNode: _editFocus,
          textAlign: TextAlign.center,
          style: TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: _player.theme.accent),
          cursorColor: _player.theme.accent,
          decoration: InputDecoration(
            border: InputBorder.none,
            hintText: line.text,
            hintStyle: TextStyle(color: Colors.white.withValues(alpha: 0.3)),
          ),
          onSubmitted: (_) => _commitEdit(index),
        ),
      );
    }

    return GestureDetector(
      key: _lineKeys[index],
      onTap: () => _player.seekToLyric(line),
      onLongPress: () => _startEdit(index, line),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 350),
        curve: Curves.easeOutCubic,
        padding: EdgeInsets.symmetric(
          horizontal: 28,
          vertical: isCurrent ? 16 : 10,
        ),
        alignment: Alignment.center,
        child: AnimatedScale(
          scale: isCurrent ? 1.0 : (isNear ? 0.97 : 0.93),
          duration: const Duration(milliseconds: 420),
          curve: Curves.easeOutBack,
          child: _lineText(line, index, isCurrent, fontSize, weight, color),
        ),
      ),
    );
  }

  Widget _lineText(LyricLine line, int index, bool isCurrent, double fontSize,
      FontWeight weight, Color color) {
    if (isCurrent && _player.lyricsMode == LyricsMode.karaoke) {
      // Only the current line repaints per frame, driven by the time notifier.
      return ValueListenableBuilder<double>(
        valueListenable: _player.timeNotifier,
        builder: (context, t, _) {
          final frac = LyricMath.revealedFraction(_player.lyrics, index, t);
          return KaraokeText(
            text: line.text,
            revealedFraction: frac,
            fontSize: fontSize,
            weight: weight,
          );
        },
      );
    }
    return Text(
      line.text,
      textAlign: TextAlign.center,
      style: TextStyle(fontSize: fontSize, fontWeight: weight, color: color),
    );
  }

  void _startEdit(int index, LyricLine line) {
    setState(() {
      _editingIndex = index;
      _editController.text = line.text;
    });
    _editFocus.requestFocus();
  }

  void _commitEdit(int index) {
    _player.editLyricLine(index, _editController.text);
    setState(() => _editingIndex = null);
  }

  Widget _loadingView() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const CircularProgressIndicator(color: Colors.white),
          const SizedBox(height: 16),
          Text('Finding lyrics...',
              style: TextStyle(color: Colors.white.withValues(alpha: 0.5))),
          if (_player.lyricsSearchStatus.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(_player.lyricsSearchStatus,
                textAlign: TextAlign.center,
                style: TextStyle(
                    fontSize: 12,
                    color: Colors.white.withValues(alpha: 0.3))),
          ],
        ],
      ),
    );
  }

  Widget _emptyView() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.text_fields,
              size: 32, color: Colors.white.withValues(alpha: 0.2)),
          const SizedBox(height: 12),
          Text('No synced lyrics found',
              style: TextStyle(color: Colors.white.withValues(alpha: 0.5))),
          const SizedBox(height: 12),
          GestureDetector(
            onTap: () =>
                _player.fetchLyricsForCurrentTrack(forceRefresh: true),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.refresh,
                      size: 14, color: Colors.white.withValues(alpha: 0.7)),
                  const SizedBox(width: 6),
                  Text('Retry',
                      style: TextStyle(
                          color: Colors.white.withValues(alpha: 0.7))),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _noTrackView() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text('No track playing',
              style: TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w600,
                  color: Colors.white.withValues(alpha: 0.5))),
          const SizedBox(height: 6),
          Text('Play a song to see lyrics here.',
              style: TextStyle(color: Colors.white.withValues(alpha: 0.25))),
        ],
      ),
    );
  }
}

/// Slider bound to the time notifier, so dragging only repaints the slider.
class _ExpandedSlider extends StatefulWidget {
  final PlayerController player;
  final double fallbackDuration;
  const _ExpandedSlider({
    required this.player,
    required this.fallbackDuration,
  });

  @override
  State<_ExpandedSlider> createState() => _ExpandedSliderState();
}

class _ExpandedSliderState extends State<_ExpandedSlider> {
  bool _dragging = false;
  double _dragValue = 0;

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<double>(
      valueListenable: widget.player.timeNotifier,
      builder: (context, liveTime, _) {
        final raw = widget.player.duration > 0
            ? widget.player.duration
            : widget.fallbackDuration;
        final duration = raw.isFinite && raw > 0 ? raw : 1.0;
        final value = (_dragging ? _dragValue : liveTime).clamp(0.0, duration);
        return Column(
          children: [
            SliderTheme(
              data: SliderTheme.of(context).copyWith(
                trackHeight: 3,
                activeTrackColor: Colors.white,
                inactiveTrackColor: Colors.white.withValues(alpha: 0.2),
                thumbColor: Colors.white,
                thumbShape:
                    const RoundSliderThumbShape(enabledThumbRadius: 6),
                overlayShape:
                    const RoundSliderOverlayShape(overlayRadius: 14),
              ),
              child: Slider(
                value: value,
                max: duration,
                onChangeStart: (v) {
                  setState(() {
                    _dragging = true;
                    _dragValue = v;
                  });
                },
                onChanged: (v) {
                  setState(() => _dragValue = v);
                },
                onChangeEnd: (v) {
                  widget.player.seek(v);
                  setState(() => _dragging = false);
                },
              ),
            ),
            Row(
              children: [
                Text(_formatTime(value),
                    style: TextStyle(
                        fontSize: 11,
                        color: Colors.white.withValues(alpha: 0.45))),
                const Spacer(),
                Text(_formatTime(duration),
                    style: TextStyle(
                        fontSize: 11,
                        color: Colors.white.withValues(alpha: 0.45))),
              ],
            ),
          ],
        );
      },
    );
  }
}

String _formatTime(double t) {
  if (!t.isFinite || t < 0) return '0:00';
  final total = t.toInt();
  return '${total ~/ 60}:${(total % 60).toString().padLeft(2, '0')}';
}
