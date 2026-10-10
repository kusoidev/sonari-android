import 'package:flutter/material.dart';

import '../models/lyric_line.dart';
import '../models/track.dart';
import '../state/player_controller.dart';
import '../widgets/cached_thumbnail.dart';
import '../widgets/theme_background.dart';

/// Lyrics tab. Port of the iOS LyricsView: a mini player header that expands
/// into full transport controls, then a center-anchored, auto-scrolling lyric
/// list with tap-to-seek and per-line editing.
class LyricsScreen extends StatefulWidget {
  final bool isActive;
  const LyricsScreen({super.key, required this.isActive});

  @override
  State<LyricsScreen> createState() => _LyricsScreenState();
}

class _LyricsScreenState extends State<LyricsScreen> {
  bool _isExpanded = false;
  final ScrollController _scrollController = ScrollController();
  int _lastScrolledIndex = -1;

  int? _editingIndex;
  final TextEditingController _editController = TextEditingController();
  final FocusNode _editFocus = FocusNode();

  @override
  void initState() {
    super.initState();
    PlayerController.instance.fetchLyricsForCurrentTrack();
    PlayerController.instance.addListener(_onPlayerChanged);
  }

  @override
  void dispose() {
    PlayerController.instance.removeListener(_onPlayerChanged);
    _scrollController.dispose();
    _editController.dispose();
    _editFocus.dispose();
    super.dispose();
  }

  void _onPlayerChanged() {
    final player = PlayerController.instance;
    if (player.currentLyricIndex != _lastScrolledIndex) {
      _lastScrolledIndex = player.currentLyricIndex;
      _scrollToCurrent();
    }
  }

  void _scrollToCurrent() {
    if (!_scrollController.hasClients) return;
    final player = PlayerController.instance;
    if (player.lyrics.isEmpty) return;

    // Each row has a roughly fixed height; scroll so the current line sits
    // near the vertical center of the viewport.
    const rowHeight = 52.0;
    final target = (player.currentLyricIndex * rowHeight) -
        (_scrollController.position.viewportDimension / 2) +
        (rowHeight / 2);
    final clamped = target.clamp(
      0.0,
      _scrollController.position.maxScrollExtent,
    );

    _scrollController.animateTo(
      clamped,
      duration: const Duration(milliseconds: 450),
      curve: Curves.easeOutCubic,
    );
  }

  @override
  Widget build(BuildContext context) {
    final player = PlayerController.instance;
    return AnimatedBuilder(
      animation: player,
      builder: (context, _) {
        return Stack(
          children: [
            const Positioned.fill(child: ThemeBackground()),
            SafeArea(
              bottom: false,
              child: player.currentTrack == null
                  ? _noTrackView()
                  : Column(
                      children: [
                        _topMiniPlayer(player, player.currentTrack!),
                        Expanded(child: _body(player)),
                      ],
                    ),
            ),
          ],
        );
      },
    );
  }

  Widget _body(PlayerController player) {
    if (player.isFetchingLyrics) return _loadingView(player);
    if (player.lyrics.isEmpty) return _emptyView();
    return _lyricsList(player);
  }

  Widget _topMiniPlayer(PlayerController player, Track track) {
    return Column(
      children: [
        InkWell(
          onTap: () => setState(() => _isExpanded = !_isExpanded),
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
                      Text(player.titleDisplay(track),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: Colors.white)),
                      const SizedBox(height: 1),
                      Text(player.artistDisplay(track),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                              fontSize: 11,
                              color: Colors.white.withValues(alpha: 0.45))),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: player.togglePlayPause,
                  icon: Icon(
                      player.isPlaying ? Icons.pause : Icons.play_arrow,
                      size: 22,
                      color: Colors.white),
                ),
                IconButton(
                  onPressed: player.nextTrack,
                  icon: Icon(Icons.skip_next,
                      size: 20, color: Colors.white.withValues(alpha: 0.55)),
                ),
              ],
            ),
          ),
        ),
        if (_isExpanded)
          _expandedControls(player, track)
        else
          _progressBar(player, track),
      ],
    );
  }

  Widget _progressBar(PlayerController player, Track track) {
    final dur = player.duration > 0
        ? player.duration
        : (track.duration ?? 0);
    final fraction = dur > 0 ? (player.currentTime / dur).clamp(0.0, 1.0) : 0.0;
    return SizedBox(
      height: 2,
      child: FractionallySizedBox(
        alignment: Alignment.centerLeft,
        widthFactor: fraction,
        child: Container(color: player.theme.accent),
      ),
    );
  }

  Widget _expandedControls(PlayerController player, Track track) {
    final duration = player.duration > 0 ? player.duration : 1.0;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 14),
      child: Column(
        children: [
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              trackHeight: 3,
              activeTrackColor: Colors.white,
              inactiveTrackColor: Colors.white.withValues(alpha: 0.2),
              thumbColor: Colors.white,
              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
              overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
            ),
            child: Slider(
              value: player.currentTime.clamp(0, duration),
              max: duration,
              onChanged: (v) => player.seek(v),
            ),
          ),
          Row(
            children: [
              Text(_formatTime(player.currentTime),
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
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              IconButton(
                onPressed: player.toggleLoop,
                icon: Icon(
                  player.loopMode == LoopMode.one
                      ? Icons.repeat_one
                      : Icons.repeat,
                  color: player.loopMode == LoopMode.none
                      ? Colors.white.withValues(alpha: 0.3)
                      : Colors.white,
                ),
              ),
              IconButton(
                onPressed: player.previousTrack,
                icon: const Icon(Icons.skip_previous,
                    size: 30, color: Colors.white),
              ),
              IconButton(
                onPressed: player.togglePlayPause,
                icon: Icon(
                    player.isPlaying
                        ? Icons.pause_circle_filled
                        : Icons.play_circle_filled,
                    size: 52,
                    color: Colors.white),
              ),
              IconButton(
                onPressed: player.nextTrack,
                icon: const Icon(Icons.skip_next,
                    size: 30, color: Colors.white),
              ),
              IconButton(
                onPressed: () => setState(() => _isExpanded = false),
                icon: Icon(Icons.close,
                    size: 18, color: Colors.white.withValues(alpha: 0.55)),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _lyricsList(PlayerController player) {
    return ListView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.symmetric(vertical: 40),
      itemCount: player.lyrics.length,
      itemBuilder: (context, index) {
        return _lyricLine(player, player.lyrics[index], index);
      },
    );
  }

  Widget _lyricLine(PlayerController player, LyricLine line, int index) {
    final offset = index - player.currentLyricIndex;
    final isCurrent = offset == 0;
    final isNear = offset.abs() <= 1;
    final isFar = offset.abs() >= 3;
    final isEditing = _editingIndex == index;

    double fontSize;
    if (isCurrent) {
      fontSize = 26;
    } else if (isNear) {
      fontSize = 20;
    } else {
      fontSize = 17;
    }

    final color = isCurrent
        ? Colors.white
        : isFar
            ? Colors.white.withValues(alpha: 0.2)
            : Colors.white.withValues(alpha: 0.4);

    if (isEditing) {
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 8),
        child: TextField(
          controller: _editController,
          focusNode: _editFocus,
          textAlign: TextAlign.center,
          style: TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: player.theme.accent),
          cursorColor: player.theme.accent,
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
      onTap: () => player.seekToLyric(line),
      onLongPress: () => _startEdit(index, line),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 350),
        curve: Curves.easeOut,
        padding: EdgeInsets.symmetric(
          horizontal: 28,
          vertical: isCurrent ? 16 : 10,
        ),
        alignment: Alignment.center,
        child: Text(
          line.text,
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: fontSize,
            fontWeight: isCurrent
                ? FontWeight.bold
                : isNear
                    ? FontWeight.w600
                    : FontWeight.w500,
            color: color,
          ),
        ),
      ),
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
    PlayerController.instance.editLyricLine(index, _editController.text);
    setState(() => _editingIndex = null);
  }

  Widget _loadingView(PlayerController player) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const CircularProgressIndicator(color: Colors.white),
          const SizedBox(height: 16),
          Text('Finding lyrics...',
              style: TextStyle(color: Colors.white.withValues(alpha: 0.5))),
          if (player.lyricsSearchStatus.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(player.lyricsSearchStatus,
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
                PlayerController.instance.fetchLyricsForCurrentTrack(
              forceRefresh: true,
            ),
            child: Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
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

  String _formatTime(double t) {
    if (!t.isFinite || t < 0) return '0:00';
    return '${t ~/ 60}:${(t.toInt() % 60).toString().padLeft(2, '0')}';
  }
}