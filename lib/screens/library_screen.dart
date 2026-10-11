import 'package:flutter/material.dart';

import '../models/track.dart';
import '../state/library_controller.dart';
import '../state/player_controller.dart';
import '../widgets/cached_thumbnail.dart';
import '../widgets/theme_background.dart';
import '../widgets/track_row.dart';

/// Library tab. Port of the iOS LibraryView: top-played strip, playlist chips,
/// sort bar, track list with rename/delete/playlist context actions.
class LibraryScreen extends StatefulWidget {
  final LibraryController controller;
  const LibraryScreen({super.key, required this.controller});

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  LibraryController get _controller => widget.controller;
  final TextEditingController _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: Listenable.merge([_controller, PlayerController.instance]),
      builder: (context, _) {
        return Stack(
          children: [
            const Positioned.fill(child: ThemeBackground()),
            SafeArea(
              bottom: false,
              child: Column(
                children: [
                  _header(),
                  _searchField(),
                  Expanded(
                    child: _controller.displayTracks.isEmpty &&
                            _controller.tracks.isEmpty
                        ? _emptyState()
                        : _trackList(),
                  ),
                ],
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _header() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
      child: Row(
        children: [
          Text(
            _controller.selectedPlaylist?.name ?? 'Library',
            style: const TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.bold,
                color: Colors.white),
          ),
          const Spacer(),
          IconButton(
            onPressed: _showNewPlaylistDialog,
            icon: const Icon(Icons.add_circle_outline,
                color: Colors.white, size: 22),
          ),
        ],
      ),
    );
  }

  Widget _searchField() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(12),
        ),
        child: TextField(
          controller: _searchController,
          style: const TextStyle(fontSize: 15, color: Colors.white),
          cursorColor: Colors.white,
          decoration: InputDecoration(
            border: InputBorder.none,
            hintText: 'Search library',
            hintStyle: TextStyle(color: Colors.white.withValues(alpha: 0.3)),
            icon: Icon(Icons.search,
                size: 18, color: Colors.white.withValues(alpha: 0.4)),
          ),
          onChanged: _controller.setSearchText,
        ),
      ),
    );
  }

  Widget _trackList() {
    final tracks = _controller.displayTracks;
    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      itemCount: tracks.length + _headerItemCount,
      itemBuilder: (context, index) {
        if (index == 0) return _topPlayedStrip();
        if (index == 1) return _playlistChips();
        if (index == 2) return _sortBar();
        final trackIndex = index - _headerItemCount;
        final track = tracks[trackIndex];
        return TrackRow(
          track: track,
          onPlay: () {
            PlayerController.instance.setQueue(tracks, startIndex: trackIndex);
            PlayerController.instance.play(track);
          },
        );
      },
    );
  }

  int get _headerItemCount => 3;

  Widget _topPlayedStrip() {
    final top = _controller.topPlayed;
    if (top.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 6, top: 8),
          child: Text('Top Played',
              style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: Colors.white.withValues(alpha: 0.5))),
        ),
        SizedBox(
          height: 46,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: top.length,
            separatorBuilder: (_, _) => const SizedBox(width: 10),
            itemBuilder: (context, i) {
              final track = top[i];
              return InkWell(
                onTap: () {
                  // Queue the whole library and start at this track, not just
                  // the 5 top-played items. Falls back to the strip if the
                  // track isn't in the current list for some reason.
                  final tracks = _controller.displayTracks;
                  final idx = tracks.indexWhere((t) => t.id == track.id);
                  if (idx >= 0) {
                    PlayerController.instance.setQueue(tracks, startIndex: idx);
                  } else {
                    PlayerController.instance.setQueue(top, startIndex: i);
                  }
                  PlayerController.instance.play(track);
                },
                child: Container(
                  width: 210,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.06),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    children: [
                      Text('${i + 1}',
                          style: TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.bold,
                              color: Colors.white.withValues(alpha: 0.7))),
                      const SizedBox(width: 6),
                      CachedThumbnail(
                        youtubeID: track.youtubeID,
                        remoteURL: track.thumbnailURL,
                        width: 34,
                        height: 34,
                        radius: 6,
                        iconSize: 12,
                      ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              PlayerController.instance.titleDisplay(track),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w500,
                                  color: Colors.white),
                            ),
                            Text('${track.playCount} plays',
                                style: TextStyle(
                                    fontSize: 10,
                                    color:
                                        Colors.white.withValues(alpha: 0.35))),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _playlistChips() {
    if (_controller.playlists.isEmpty) return const SizedBox(height: 8);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: SizedBox(
        height: 32,
        child: ListView(
          scrollDirection: Axis.horizontal,
          children: [
            _chip('All', _controller.selectedPlaylist == null,
                () => _controller.selectPlaylist(null)),
            ..._controller.playlists.map((p) {
              return Padding(
                padding: const EdgeInsets.only(left: 8),
                child: GestureDetector(
                  onLongPress: () => _playlistOptions(p),
                  child: _chip(
                    p.name,
                    _controller.selectedPlaylist?.id == p.id,
                    () => _controller.selectPlaylist(p),
                  ),
                ),
              );
            }),
          ],
        ),
      ),
    );
  }

  Widget _chip(String label, bool isSelected, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
        decoration: BoxDecoration(
          color: isSelected ? Colors.white : Colors.white.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 13,
            fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
            color: isSelected ? Colors.black : Colors.white.withValues(alpha: 0.65),
          ),
        ),
      ),
    );
  }

  Widget _sortBar() {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        children: SortOrder.values.map((order) {
          final isSelected = _controller.sortOrder == order;
          return Expanded(
            child: GestureDetector(
              onTap: () => _controller.setSortOrder(order),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 8),
                decoration: BoxDecoration(
                  border: Border(
                    bottom: BorderSide(
                      color: isSelected
                          ? Colors.white
                          : Colors.transparent,
                      width: 1.5,
                    ),
                  ),
                ),
                child: Text(
                  order.label,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight:
                        isSelected ? FontWeight.w600 : FontWeight.w400,
                    color: isSelected
                        ? Colors.white
                        : Colors.white.withValues(alpha: 0.35),
                  ),
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }

  Widget _emptyState() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.library_music,
              size: 44, color: Colors.white.withValues(alpha: 0.15)),
          const SizedBox(height: 14),
          Text(
            _controller.tracks.isEmpty ? 'No tracks yet' : 'No results',
            style: TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w600,
                color: Colors.white.withValues(alpha: 0.4)),
          ),
          if (_controller.tracks.isEmpty) ...[
            const SizedBox(height: 6),
            Text('Head to Download to grab some music.',
                style: TextStyle(color: Colors.white.withValues(alpha: 0.2))),
          ],
        ],
      ),
    );
  }

  Future<void> _showNewPlaylistDialog() async {
    final textController = TextEditingController();
    final name = await showDialog<String>(
      context: context,
      builder: (context) {
        return AlertDialog(
          backgroundColor: const Color(0xFF1A1A1E),
          title: const Text('New Playlist',
              style: TextStyle(color: Colors.white)),
          content: TextField(
            controller: textController,
            autofocus: true,
            style: const TextStyle(color: Colors.white),
            decoration: const InputDecoration(
              hintText: 'Playlist name',
              hintStyle: TextStyle(color: Colors.white54),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () =>
                  Navigator.pop(context, textController.text.trim()),
              child: const Text('Create'),
            ),
          ],
        );
      },
    );
    if (name != null && name.isNotEmpty) {
      _controller.createPlaylist(name);
    }
  }

  Future<void> _playlistOptions(Playlist playlist) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: const Color(0xFF1A1A1E),
      builder: (context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: const Icon(Icons.edit, color: Colors.white),
                title: const Text('Rename',
                    style: TextStyle(color: Colors.white)),
                onTap: () => Navigator.pop(context, 'rename'),
              ),
              ListTile(
                leading: const Icon(Icons.delete, color: Colors.red),
                title: const Text('Delete',
                    style: TextStyle(color: Colors.red)),
                onTap: () => Navigator.pop(context, 'delete'),
              ),
            ],
          ),
        );
      },
    );

    if (action == 'delete') {
      _controller.deletePlaylist(playlist);
    }
  }
}