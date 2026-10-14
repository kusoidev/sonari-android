import 'package:flutter/material.dart';

import '../models/track.dart';
import '../state/player_controller.dart';
import 'cached_thumbnail.dart';

/// One library row: artwork with a playing overlay, title/artist/duration,
/// and a play/pause button. Port of TrackRowView.
class TrackRow extends StatelessWidget {
  final Track track;
  final VoidCallback onPlay;
  final VoidCallback? onLongPress;

  const TrackRow({
    super.key,
    required this.track,
    required this.onPlay,
    this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: PlayerController.instance,
      builder: (context, _) {
        final player = PlayerController.instance;
        final isCurrent = player.currentTrack?.id == track.id;
        final isPlaying = isCurrent && player.isPlaying;

        return InkWell(
          onTap: onPlay,
          onLongPress: onLongPress,
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Row(
              children: [
                Stack(
                  alignment: Alignment.center,
                  children: [
                    CachedThumbnail(
                      youtubeID: track.youtubeID,
                      remoteURL: track.thumbnailURL,
                      width: 48,
                      height: 48,
                      iconSize: 14,
                    ),
                    if (isCurrent)
                      Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.4),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Icon(
                          isPlaying ? Icons.pause : Icons.play_arrow,
                          size: 18,
                          color: Colors.white,
                        ),
                      ),
                  ],
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        player.titleDisplay(track),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight:
                              isCurrent ? FontWeight.w600 : FontWeight.w500,
                          color: isCurrent
                              ? Colors.white
                              : Colors.white.withValues(alpha: 0.9),
                        ),
                      ),
                      const SizedBox(height: 3),
                      Row(
                        children: [
                          if (isCurrent) ...[
                            Icon(
                              Icons.graphic_eq,
                              size: 11,
                              color: Colors.white.withValues(alpha: 0.5),
                            ),
                            const SizedBox(width: 4),
                          ],
                          Flexible(
                            child: Text(
                              player.artistDisplay(track),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.white
                                    .withValues(alpha: isCurrent ? 0.5 : 0.35),
                              ),
                            ),
                          ),
                          if (track.duration != null && track.duration! > 0) ...[
                            Text(
                              ' · ',
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.white.withValues(alpha: 0.2),
                              ),
                            ),
                            Text(
                              _formatDuration(track.duration!),
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.white.withValues(alpha: 0.25),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: onPlay,
                  icon: Icon(
                    isPlaying ? Icons.pause_circle : Icons.play_circle,
                    size: 26,
                    color: Colors.white.withValues(alpha: 0.5),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  String _formatDuration(double t) {
    if (!t.isFinite || t <= 0) return '';
    final m = t ~/ 60;
    final s = t.toInt() % 60;
    return '$m:${s.toString().padLeft(2, '0')}';
  }
}