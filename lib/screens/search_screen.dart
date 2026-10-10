import 'package:flutter/material.dart';

import '../models/track.dart';
import '../models/youtube_result.dart';
import '../services/download_service.dart';
import '../services/library_manager.dart';
import '../state/player_controller.dart';
import '../state/search_controller.dart';
import '../widgets/cached_thumbnail.dart';
import '../widgets/theme_background.dart';

/// Search tab. Port of the iOS SearchView: query bar, results list with
/// per-row download and play actions, plus a context menu.
class SearchScreen extends StatefulWidget {
  final MusicSearchController controller;
  const SearchScreen({super.key, required this.controller});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  MusicSearchController get _controller => widget.controller;
  final TextEditingController _textController = TextEditingController();

  @override
  void dispose() {
    _textController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        return Stack(
          children: [
            const Positioned.fill(child: ThemeBackground()),
            SafeArea(
              bottom: false,
              child: Column(
                children: [
                  const SizedBox(height: 8),
                  _searchBar(),
                  const SizedBox(height: 8),
                  Expanded(child: _body()),
                ],
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _searchBar() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(
        children: [
          Expanded(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 2),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                children: [
                  Icon(Icons.search,
                      size: 18, color: Colors.white.withValues(alpha: 0.4)),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: _textController,
                      style: const TextStyle(fontSize: 15, color: Colors.white),
                      cursorColor: Colors.white,
                      textInputAction: TextInputAction.search,
                      decoration: InputDecoration(
                        border: InputBorder.none,
                        hintText: 'Artists, songs, videos…',
                        hintStyle: TextStyle(
                            color: Colors.white.withValues(alpha: 0.3)),
                      ),
                      onChanged: (v) => _controller.query = v,
                      onSubmitted: (_) => _controller.search(),
                    ),
                  ),
                  if (_controller.query.isNotEmpty)
                    GestureDetector(
                      onTap: () {
                        _textController.clear();
                        _controller.clear();
                      },
                      child: Icon(Icons.cancel,
                          size: 18,
                          color: Colors.white.withValues(alpha: 0.3)),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _body() {
    if (_controller.isLoading) {
      return _centered([
        const CircularProgressIndicator(color: Colors.white),
        const SizedBox(height: 14),
        Text('Searching…',
            style: TextStyle(color: Colors.white.withValues(alpha: 0.35))),
      ]);
    }
    if (_controller.errorMessage != null) {
      return _centered([
        Icon(Icons.wifi_off,
            size: 40, color: Colors.white.withValues(alpha: 0.2)),
        const SizedBox(height: 14),
        Text(_controller.errorMessage!,
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.white.withValues(alpha: 0.35))),
      ]);
    }
    if (_controller.results.isNotEmpty) {
      return _resultsList();
    }
    return _centered([
      Icon(Icons.search,
          size: 44, color: Colors.white.withValues(alpha: 0.1)),
      const SizedBox(height: 14),
      Text('Find your music',
          style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w600,
              color: Colors.white.withValues(alpha: 0.3))),
      const SizedBox(height: 6),
      Text('Search YouTube and play anything instantly.',
          textAlign: TextAlign.center,
          style: TextStyle(color: Colors.white.withValues(alpha: 0.18))),
    ]);
  }

  Widget _centered(List<Widget> children) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: children,
      ),
    );
  }

  Widget _resultsList() {
    return ListView.builder(
      padding: const EdgeInsets.only(bottom: 8),
      itemCount: _controller.results.length,
      itemBuilder: (context, index) {
        return _resultRow(_controller.results[index]);
      },
    );
  }

  Widget _resultRow(YouTubeSearchResult result) {
    return InkWell(
      onLongPress: () => _showContextMenu(result),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        child: Row(
          children: [
            Stack(
              children: [
                CachedThumbnail(
                  youtubeID: result.id,
                  remoteURL: result.thumbnailURL,
                  width: 100,
                  height: 58,
                  iconSize: 20,
                ),
                if (result.duration != null)
                  Positioned(
                    right: 5,
                    bottom: 5,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 5, vertical: 2),
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.7),
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(
                        result.duration!,
                        style: const TextStyle(
                            fontSize: 9,
                            fontWeight: FontWeight.w600,
                            color: Colors.white),
                      ),
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
                    result.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w500,
                        color: Colors.white),
                  ),
                  if (result.channelName != null) ...[
                    const SizedBox(height: 4),
                    Text(
                      result.channelName!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          fontSize: 11,
                          color: Colors.white.withValues(alpha: 0.4)),
                    ),
                  ],
                ],
              ),
            ),
            IconButton(
              onPressed: () => _enqueueDownload(result),
              icon: Icon(Icons.download_for_offline_outlined,
                  size: 24, color: Colors.white.withValues(alpha: 0.5)),
            ),
            IconButton(
              onPressed: () => _streamNow(result),
              icon: Icon(Icons.play_circle_fill,
                  size: 30, color: Colors.white.withValues(alpha: 0.7)),
            ),
          ],
        ),
      ),
    );
  }

  void _showContextMenu(YouTubeSearchResult result) {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1A1A1E),
      builder: (context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: const Icon(Icons.play_arrow, color: Colors.white),
                title: const Text('Play Now',
                    style: TextStyle(color: Colors.white)),
                onTap: () {
                  Navigator.pop(context);
                  _streamNow(result);
                },
              ),
              ListTile(
                leading:
                    const Icon(Icons.download, color: Colors.white),
                title: const Text('Download',
                    style: TextStyle(color: Colors.white)),
                onTap: () {
                  Navigator.pop(context);
                  _enqueueDownload(result);
                },
              ),
              ListTile(
                leading:
                    const Icon(Icons.refresh, color: Colors.white),
                title: const Text('Reset Lyrics',
                    style: TextStyle(color: Colors.white)),
                onTap: () {
                  Navigator.pop(context);
                  final resetTrack = Track(
                    title: result.title,
                    youtubeID: result.id,
                    thumbnailURL: result.thumbnailURL,
                    artist: result.channelName,
                  );
                  PlayerController.instance.resetLyrics(resetTrack);
                },
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _streamNow(YouTubeSearchResult result) async {
    final docs = LibraryManager.instance.docsPath;
    for (final existing in LibraryManager.instance.tracks) {
      if (existing.youtubeID == result.id && existing.fileName != null) {
        final file = existing.localFileSync(docs);
        if (file != null && file.existsSync()) {
          PlayerController.instance.play(existing);
          return;
        }
      }
    }

    final track = Track(
      title: result.title,
      youtubeID: result.id,
      thumbnailURL: result.thumbnailURL,
      artist: result.channelName,
    );
    PlayerController.instance.play(track);
  }

  void _enqueueDownload(YouTubeSearchResult result) {
    DownloadService.instance.enqueue(
      videoID: result.id,
      title: result.title,
      thumbnailURL: result.thumbnailURL,
      artist: result.channelName,
    );
  }
}