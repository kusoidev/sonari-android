import 'package:flutter/material.dart';

import '../models/track.dart';
import '../state/player_controller.dart';
import '../widgets/cached_thumbnail.dart';
import '../widgets/theme_background.dart';

/// Player tab. Port of the iOS PlayerView: artwork that shrinks into a header
/// when the queue is shown, seek bar, transport controls, and the up-next list.
class PlayerScreen extends StatefulWidget {
  const PlayerScreen({super.key});

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> {
  bool _showingQueue = false;
  bool _isDragging = false;
  double _dragValue = 0;

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
                  ? _emptyState()
                  : _content(player, player.currentTrack!),
            ),
          ],
        );
      },
    );
  }

  Widget _content(PlayerController player, Track track) {
    return Column(
      children: [
        if (!_showingQueue) const Spacer(),
        _header(player, track),
        if (_showingQueue)
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(top: 20),
              child: _upNextList(player),
            ),
          )
        else
          const Spacer(),
        Column(
          children: [
            if (player.isDownloading)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 28),
                child: _downloadProgress(player),
              )
            else
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 28),
                child: _seekBar(player, track),
              ),
            const SizedBox(height: 20),
            _controls(player),
          ],
        ),
        const SizedBox(height: 24),
      ],
    );
  }

  Widget _header(PlayerController player, Track track) {
    final side = _showingQueue
        ? 56.0
        : (MediaQuery.of(context).size.width - 64).clamp(0, 380).toDouble();

    if (_showingQueue) {
      return Padding(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
        child: Row(
          children: [
            GestureDetector(
              onTap: _toggleQueue,
              child: CachedThumbnail(
                youtubeID: track.youtubeID,
                remoteURL: track.thumbnailURL,
                width: side,
                height: side,
                radius: 12,
                iconSize: 20,
              ),
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
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: Colors.white)),
                  const SizedBox(height: 2),
                  Text(player.artistDisplay(track),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          fontSize: 13,
                          color: Colors.white.withValues(alpha: 0.5))),
                ],
              ),
            ),
          ],
        ),
      );
    }

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32),
          child: GestureDetector(
            onTap: _toggleQueue,
            child: CachedThumbnail(
              youtubeID: track.youtubeID,
              remoteURL: track.thumbnailURL,
              width: side,
              height: side,
              radius: 12,
              iconSize: 56,
            ),
          ),
        ),
        const SizedBox(height: 28),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32),
          child: Column(
            children: [
              Text(player.titleDisplay(track),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.bold,
                      color: Colors.white)),
              const SizedBox(height: 6),
              Text(player.artistDisplay(track),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                      fontSize: 15,
                      color: Colors.white.withValues(alpha: 0.5))),
            ],
          ),
        ),
      ],
    );
  }

  void _toggleQueue() {
    setState(() => _showingQueue = !_showingQueue);
  }

  Widget _seekBar(PlayerController player, Track track) {
    final rawDuration = player.duration;
    final duration = rawDuration.isFinite && rawDuration > 0 ? rawDuration : 1.0;
    final current = _isDragging ? _dragValue : player.currentTime;
    final fraction = (current / duration).clamp(0.0, 1.0);

    return Column(
      children: [
        LayoutBuilder(
          builder: (context, constraints) {
            final width = constraints.maxWidth;
            return GestureDetector(
              behavior: HitTestBehavior.opaque,
              onHorizontalDragStart: (d) {
                setState(() {
                  _isDragging = true;
                  _dragValue =
                      (d.localPosition.dx / width * duration).clamp(0, duration);
                });
              },
              onHorizontalDragUpdate: (d) {
                setState(() {
                  _dragValue =
                      (d.localPosition.dx / width * duration).clamp(0, duration);
                });
              },
              onHorizontalDragEnd: (_) {
                player.seek(_dragValue);
                setState(() => _isDragging = false);
              },
              onTapDown: (d) {
                final value = (d.localPosition.dx / width * duration)
                    .clamp(0.0, duration)
                    .toDouble();
                player.seek(value);
              },
              child: SizedBox(
                height: 20,
                child: Stack(
                  alignment: Alignment.centerLeft,
                  children: [
                    Container(
                      height: 4,
                      decoration: BoxDecoration(
                        color: Colors.white.withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                    FractionallySizedBox(
                      widthFactor: fraction,
                      child: Container(
                        height: 4,
                        decoration: BoxDecoration(
                          color: player.theme.accent,
                          borderRadius: BorderRadius.circular(2),
                        ),
                      ),
                    ),
                    Positioned(
                      left: (fraction * width) - (_isDragging ? 8 : 6),
                      child: Container(
                        width: _isDragging ? 16 : 12,
                        height: _isDragging ? 16 : 12,
                        decoration: BoxDecoration(
                          color: player.theme.accent,
                          shape: BoxShape.circle,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Text(_formatTime(current),
                style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w500,
                    color: Colors.white.withValues(alpha: 0.4))),
            const Spacer(),
            Text(_formatTime(duration),
                style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w500,
                    color: Colors.white.withValues(alpha: 0.4))),
          ],
        ),
      ],
    );
  }

  Widget _downloadProgress(PlayerController player) {
    return Column(
      children: [
        Row(
          children: [
            const SizedBox(
                width: 14,
                height: 14,
                child: CircularProgressIndicator(
                    strokeWidth: 2, color: Colors.white)),
            const SizedBox(width: 8),
            Text('Preparing download...',
                style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                    color: Colors.white.withValues(alpha: 0.6))),
            const Spacer(),
            Text('${(player.downloadProgress * 100).toInt()}%',
                style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: Colors.white.withValues(alpha: 0.4))),
          ],
        ),
        const SizedBox(height: 12),
        ClipRRect(
          borderRadius: BorderRadius.circular(2),
          child: LinearProgressIndicator(
            value: player.downloadProgress.clamp(0, 1),
            minHeight: 4,
            backgroundColor: Colors.white.withValues(alpha: 0.12),
            valueColor: const AlwaysStoppedAnimation(Colors.white),
          ),
        ),
      ],
    );
  }

  Widget _controls(PlayerController player) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Row(
        children: [
          Expanded(
            child: _smallControl(
              icon: player.loopMode == LoopMode.one
                  ? Icons.repeat_one
                  : Icons.repeat,
              label: _loopLabel(player.loopMode),
              active: player.loopMode != LoopMode.none,
              onTap: player.toggleLoop,
            ),
          ),
          Expanded(
            child: IconButton(
              onPressed: player.previousTrack,
              icon: Icon(Icons.skip_previous,
                  size: 32, color: Colors.white.withValues(alpha: 0.85)),
            ),
          ),
          Expanded(
            child: GestureDetector(
              onTap: player.togglePlayPause,
              child: Container(
                width: 68,
                height: 68,
                decoration: BoxDecoration(
                  color: player.theme.accent,
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: player.theme.accent.withValues(alpha: 0.35),
                      blurRadius: 12,
                    ),
                  ],
                ),
                child: Icon(
                  player.isPlaying ? Icons.pause : Icons.play_arrow,
                  size: 34,
                  color: player.theme.onAccent,
                ),
              ),
            ),
          ),
          Expanded(
            child: IconButton(
              onPressed: player.nextTrack,
              icon: Icon(Icons.skip_next,
                  size: 32, color: Colors.white.withValues(alpha: 0.85)),
            ),
          ),
          Expanded(
            child: _smallControl(
              icon: Icons.shuffle,
              label: 'Shuffle',
              active: player.isShuffled,
              onTap: player.shuffleQueue,
            ),
          ),
        ],
      ),
    );
  }

  Widget _smallControl({
    required IconData icon,
    required String label,
    required bool active,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon,
              size: 20,
              color: active
                  ? Colors.white
                  : Colors.white.withValues(alpha: 0.3)),
          const SizedBox(height: 3),
          Text(label,
              style: TextStyle(
                  fontSize: 8,
                  fontWeight: FontWeight.w600,
                  color: active
                      ? Colors.white
                      : Colors.white.withValues(alpha: 0.3))),
        ],
      ),
    );
  }

  Widget _upNextList(PlayerController player) {
    final queue = player.queue;
    if (queue.length <= 1) return const SizedBox.shrink();

    final current = player.queueIndex;
    final upcoming = List.generate(
      queue.length - 1,
      (i) => (current + 1 + i) % queue.length,
    );

    return ListView(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 0, 24, 12),
          child: Text('Up Next',
              style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: Colors.white.withValues(alpha: 0.5))),
        ),
        for (var offset = 0; offset < upcoming.length; offset++)
          _queueRow(player, queue[upcoming[offset]], offset + 1,
              upcoming[offset], offset == 0),
      ],
    );
  }

  Widget _queueRow(PlayerController player, Track track, int number,
      int index, bool isNext) {
    return InkWell(
      onTap: () => player.playTrackAt(index),
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        decoration: BoxDecoration(
          color: isNext
              ? Colors.white.withValues(alpha: 0.04)
              : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            SizedBox(
              width: 18,
              child: Text('$number',
                  style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: isNext
                          ? Colors.white.withValues(alpha: 0.8)
                          : Colors.white.withValues(alpha: 0.35))),
            ),
            const SizedBox(width: 12),
            CachedThumbnail(
              youtubeID: track.youtubeID,
              remoteURL: track.thumbnailURL,
              width: 42,
              height: 42,
              iconSize: 14,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(player.titleDisplay(track),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          fontSize: 14,
                          fontWeight:
                              isNext ? FontWeight.w600 : FontWeight.w500,
                          color: isNext
                              ? Colors.white
                              : Colors.white.withValues(alpha: 0.8))),
                  const SizedBox(height: 2),
                  Text(player.artistDisplay(track),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          fontSize: 11,
                          color: Colors.white.withValues(alpha: 0.35))),
                ],
              ),
            ),
            if (isNext)
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text('NEXT',
                    style: TextStyle(
                        fontSize: 9,
                        fontWeight: FontWeight.bold,
                        color: Colors.white.withValues(alpha: 0.4))),
              ),
          ],
        ),
      ),
    );
  }

  Widget _emptyState() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.play_circle_outline,
              size: 52, color: Colors.white.withValues(alpha: 0.15)),
          const SizedBox(height: 16),
          Text('Nothing playing',
              style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                  color: Colors.white.withValues(alpha: 0.4))),
          const SizedBox(height: 6),
          Text('Pick a track from your library.',
              style: TextStyle(color: Colors.white.withValues(alpha: 0.2))),
        ],
      ),
    );
  }

  String _loopLabel(LoopMode mode) {
    switch (mode) {
      case LoopMode.none:
        return 'Off';
      case LoopMode.one:
        return 'One';
      case LoopMode.all:
        return 'All';
    }
  }

  String _formatTime(double t) {
    if (!t.isFinite || t < 0) return '0:00';
    return '${t ~/ 60}:${(t.toInt() % 60).toString().padLeft(2, '0')}';
  }
}