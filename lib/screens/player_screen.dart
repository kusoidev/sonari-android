import 'package:flutter/material.dart';

import '../models/track.dart';
import '../state/player_controller.dart';
import '../widgets/cached_thumbnail.dart';
import '../widgets/theme_background.dart';

/// Player tab. Port of the iOS PlayerView: artwork that shrinks into a header
/// when the queue is shown, seek bar, transport controls, and the up-next list.
/// The header state change is animated with a spring (AnimatedSize + a scale
/// cross-fade), the seek bar and time labels rebuild off the time notifier, and
/// the play button springs on tap.
class PlayerScreen extends StatefulWidget {
  const PlayerScreen({super.key});

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> {
  bool _showingQueue = false;
  bool _isDragging = false;
  double _dragValue = 0;

  // Settle timing for the header morph and queue reveal. easeInOutCubic is
  // roughly 50% done at the halfway point, so short moves stay perceptible for
  // the whole duration. The old (0.22, 0.9, 0.32, 1.0) curve was 94% done at
  // halfway, and easeOutCubic was 88% done, so both read as instant snaps.
  static const _queueSpring = Duration(milliseconds: 380);
  static const _queueCurve = Curves.easeInOutCubic;

  PlayerController get _player => PlayerController.instance;

  void _toggleQueue() {
    setState(() => _showingQueue = !_showingQueue);
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
                  ? _emptyState()
                  : _content(track),
            ),
          ],
        );
      },
    );
  }

  Widget _content(Track track) {
    return Column(
      children: [
        if (!_showingQueue) const Spacer(),
        AnimatedSize(
          duration: _queueSpring,
          curve: _queueCurve,
          alignment: Alignment.topCenter,
          child: _header(track),
        ),
        if (_showingQueue)
          Expanded(
            child: TweenAnimationBuilder<double>(
              key: const ValueKey('queue-list'),
              tween: Tween<double>(begin: 0, end: 1),
              duration: _queueSpring,
              curve: _queueCurve,
              builder: (context, t, child) {
                return Opacity(
                  opacity: t,
                  child: Transform.translate(
                    offset: Offset(0, (1 - t) * 24),
                    child: child,
                  ),
                );
              },
              child: Padding(
                padding: const EdgeInsets.only(top: 20),
                child: _upNextList(),
              ),
            ),
          )
        else
          const Spacer(),
        Column(
          children: [
            if (_player.isDownloading)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 28),
                child: _downloadProgress(),
              )
            else
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 28),
                child: _seekBar(track),
              ),
            const SizedBox(height: 20),
            _controls(),
          ],
        ),
        const SizedBox(height: 24),
      ],
    );
  }

  /// One header that morphs between the full-screen layout and the compact
  /// queue layout, instead of swapping two different widgets. The artwork and
  /// the title/artist block are single elements that travel and resize, the
  /// same idea as the iOS matchedGeometryEffect. Cross-fading two layouts (the
  /// old AnimatedSwitcher) read as a pop because the artwork never moved.
  Widget _header(Track track) {
    final width = MediaQuery.of(context).size.width;
    final fullSide = (width - 64).clamp(0, 380).toDouble();
    final side = _showingQueue ? 56.0 : fullSide;

    final artLeft = _showingQueue ? 20.0 : (width - fullSide) / 2;
    final artTop = _showingQueue ? 12.0 : 0.0;

    final textTop = _showingQueue ? 12.0 : fullSide + 28;
    final textLeft = _showingQueue ? 88.0 : 32.0;
    final textRight = _showingQueue ? 20.0 : 32.0;

    final headerHeight = _showingQueue ? 80.0 : fullSide + 84;

    return SizedBox(
      width: width,
      height: headerHeight,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          AnimatedPositioned(
            duration: _queueSpring,
            curve: _queueCurve,
            left: artLeft,
            top: artTop,
            child: GestureDetector(
              onTap: _toggleQueue,
              child: _artwork(track, side),
            ),
          ),
          AnimatedPositioned(
            duration: _queueSpring,
            curve: _queueCurve,
            left: textLeft,
            right: textRight,
            top: textTop,
            child: _headerText(track),
          ),
        ],
      ),
    );
  }

  Widget _headerText(Track track) {
    final align =
        _showingQueue ? CrossAxisAlignment.start : CrossAxisAlignment.center;
    final textAlign = _showingQueue ? TextAlign.left : TextAlign.center;
    return Column(
      crossAxisAlignment: align,
      mainAxisSize: MainAxisSize.min,
      children: [
        AnimatedDefaultTextStyle(
          duration: _queueSpring,
          curve: _queueCurve,
          style: TextStyle(
            fontSize: _showingQueue ? 16 : 22,
            fontWeight: FontWeight.bold,
            color: Colors.white,
          ),
          child: Text(
            _player.titleDisplay(track),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textAlign: textAlign,
          ),
        ),
        const SizedBox(height: 4),
        AnimatedDefaultTextStyle(
          duration: _queueSpring,
          curve: _queueCurve,
          style: TextStyle(
            fontSize: _showingQueue ? 13 : 15,
            color: Colors.white.withValues(alpha: 0.5),
          ),
          child: Text(
            _player.artistDisplay(track),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textAlign: textAlign,
          ),
        ),
      ],
    );
  }

  Widget _artwork(Track track, double side) {
    return AnimatedContainer(
      duration: _queueSpring,
      curve: _queueCurve,
      width: side,
      height: side,
      child: CachedThumbnail(
        youtubeID: track.youtubeID,
        remoteURL: track.thumbnailURL,
        width: side,
        height: side,
        radius: 12,
        iconSize: side > 100 ? 56 : 20,
      ),
    );
  }

  Widget _seekBar(Track track) {
    return Column(
      children: [
        ValueListenableBuilder<double>(
          valueListenable: _player.timeNotifier,
          builder: (context, liveTime, _) {
            final rawDuration = _player.duration;
            final duration =
                rawDuration.isFinite && rawDuration > 0 ? rawDuration : 1.0;
            final current = _isDragging ? _dragValue : liveTime;
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
                              (d.localPosition.dx / width * duration)
                                  .clamp(0, duration);
                        });
                      },
                      onHorizontalDragUpdate: (d) {
                        setState(() {
                          _dragValue =
                              (d.localPosition.dx / width * duration)
                                  .clamp(0, duration);
                        });
                      },
                      onHorizontalDragEnd: (_) {
                        _player.seek(_dragValue);
                        setState(() => _isDragging = false);
                      },
                      onTapDown: (d) {
                        final value = (d.localPosition.dx / width * duration)
                            .clamp(0.0, duration)
                            .toDouble();
                        _player.seek(value);
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
                                  color: _player.theme.accent,
                                  borderRadius: BorderRadius.circular(2),
                                ),
                              ),
                            ),
                            Positioned(
                              left: (fraction * width) -
                                  (_isDragging ? 8 : 6),
                              child: AnimatedContainer(
                                duration: const Duration(milliseconds: 180),
                                curve: Curves.easeOutCubic,
                                width: _isDragging ? 16 : 12,
                                height: _isDragging ? 16 : 12,
                                decoration: BoxDecoration(
                                  color: _player.theme.accent,
                                  shape: BoxShape.circle,
                                  boxShadow: [
                                    BoxShadow(
                                      color: Colors.black.withValues(alpha: 0.3),
                                      blurRadius: 3,
                                    ),
                                  ],
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
          },
        ),
      ],
    );
  }

  Widget _downloadProgress() {
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
            Text('${(_player.downloadProgress * 100).toInt()}%',
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
            value: _player.downloadProgress.clamp(0, 1),
            minHeight: 4,
            backgroundColor: Colors.white.withValues(alpha: 0.12),
            valueColor: const AlwaysStoppedAnimation(Colors.white),
          ),
        ),
      ],
    );
  }

  Widget _controls() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Row(
        children: [
          Expanded(
            child: _smallControl(
              icon: _player.loopMode == LoopMode.one
                  ? Icons.repeat_one
                  : Icons.repeat,
              label: _loopLabel(_player.loopMode),
              active: _player.loopMode != LoopMode.none,
              onTap: _player.toggleLoop,
            ),
          ),
          Expanded(
            child: IconButton(
              onPressed: _player.previousTrack,
              icon: Icon(Icons.skip_previous,
                  size: 32, color: Colors.white.withValues(alpha: 0.85)),
            ),
          ),
          Expanded(child: _PlayButton(player: _player)),
          Expanded(
            child: IconButton(
              onPressed: _player.nextTrack,
              icon: Icon(Icons.skip_next,
                  size: 32, color: Colors.white.withValues(alpha: 0.85)),
            ),
          ),
          Expanded(
            child: _smallControl(
              icon: Icons.shuffle,
              label: 'Shuffle',
              active: _player.isShuffled,
              onTap: _player.shuffleQueue,
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
          AnimatedScale(
            scale: active ? 1.0 : 0.92,
            duration: const Duration(milliseconds: 220),
            curve: Curves.easeOutCubic,
            child: Icon(icon,
                size: 20,
                color: active
                    ? Colors.white
                    : Colors.white.withValues(alpha: 0.3)),
          ),
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

  Widget _upNextList() {
    final queue = _player.queue;
    if (queue.length <= 1) return const SizedBox.shrink();

    final current = _player.queueIndex;
    final upcomingCount = queue.length - 1;

    return ListView.builder(
      itemCount: upcomingCount + 1,
      itemBuilder: (context, index) {
        if (index == 0) {
          return Padding(
            padding: const EdgeInsets.fromLTRB(24, 0, 24, 12),
            child: Text('Up Next',
                style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                    color: Colors.white.withValues(alpha: 0.5))),
          );
        }
        final offset = index - 1;
        final queuePos = (current + 1 + offset) % queue.length;
        return _queueRow(
          queue[queuePos],
          offset + 1,
          queuePos,
          offset == 0,
          offset,
        );
      },
    );
  }

  Widget _queueRow(
      Track track, int number, int index, bool isNext, int offset) {
    return TweenAnimationBuilder<double>(
      key: ValueKey('queue-$index'),
      tween: Tween<double>(begin: 0, end: 1),
      duration: Duration(milliseconds: 260 + (offset.clamp(0, 6) * 40)),
      curve: Curves.easeOutCubic,
      builder: (context, t, child) {
        return Opacity(
          opacity: t,
          child: Transform.translate(
            offset: Offset(0, (1 - t) * 12),
            child: child,
          ),
        );
      },
      child: InkWell(
        onTap: () => _player.playTrackAt(index),
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
                    Text(_player.titleDisplay(track),
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
                    Text(_player.artistDisplay(track),
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

/// The big accent play/pause circle. Springs on press and eases the icon swap.
class _PlayButton extends StatefulWidget {
  final PlayerController player;
  const _PlayButton({required this.player});

  @override
  State<_PlayButton> createState() => _PlayButtonState();
}

class _PlayButtonState extends State<_PlayButton> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final accent = widget.player.theme.accent;
    return GestureDetector(
      onTapDown: (_) => setState(() => _pressed = true),
      onTapUp: (_) => setState(() => _pressed = false),
      onTapCancel: () => setState(() => _pressed = false),
      onTap: widget.player.togglePlayPause,
      child: AnimatedScale(
        scale: _pressed ? 0.92 : 1.0,
        duration: const Duration(milliseconds: 140),
        curve: Curves.easeOut,
        child: Container(
          width: 68,
          height: 68,
          decoration: BoxDecoration(
            color: accent,
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                color: accent.withValues(alpha: 0.35),
                blurRadius: 12,
              ),
            ],
          ),
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 200),
            transitionBuilder: (child, animation) => ScaleTransition(
              scale: animation,
              child: FadeTransition(opacity: animation, child: child),
            ),
            child: Icon(
              widget.player.isPlaying ? Icons.pause : Icons.play_arrow,
              key: ValueKey(widget.player.isPlaying),
              size: 34,
              color: widget.player.theme.onAccent,
            ),
          ),
        ),
      ),
    );
  }
}
