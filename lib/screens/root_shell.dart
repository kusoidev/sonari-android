import 'package:flutter/material.dart';

import '../state/download_controller.dart';
import '../state/library_controller.dart';
import '../state/player_controller.dart';
import '../state/search_controller.dart';
import '../widgets/theme_background.dart';
import 'download_screen.dart';
import 'library_screen.dart';
import 'lyrics_screen.dart';
import 'player_screen.dart';
import 'search_screen.dart';

enum Tab { search, library, lyrics, player, download }

extension TabInfo on Tab {
  String get label {
    switch (this) {
      case Tab.search:
        return 'Search';
      case Tab.library:
        return 'Library';
      case Tab.lyrics:
        return 'Lyrics';
      case Tab.player:
        return 'Player';
      case Tab.download:
        return 'Download';
    }
  }

  IconData get icon {
    switch (this) {
      case Tab.search:
        return Icons.search;
      case Tab.library:
        return Icons.library_music;
      case Tab.lyrics:
        return Icons.chat_bubble_outline;
      case Tab.player:
        return Icons.play_circle_outline;
      case Tab.download:
        return Icons.download;
    }
  }
}

/// Hosts the five tabs behind a custom bottom bar. Only the active tab is
/// built, matching the iOS fix for the nav-bar layout crash and the per-tab
/// layout cost. State that must survive switches lives in the controllers.
class RootShell extends StatefulWidget {
  const RootShell({super.key});

  @override
  State<RootShell> createState() => _RootShellState();
}

class _RootShellState extends State<RootShell> {
  Tab _selected = Tab.library;

  // Hoisted so search results, library state, and the paste field survive tab
  // switches, matching the iOS ContentView which owns these view models.
  final MusicSearchController _searchController = MusicSearchController();
  final LibraryController _libraryController = LibraryController();
  final DownloadController _downloadController = DownloadController();

  @override
  void dispose() {
    _searchController.dispose();
    _libraryController.dispose();
    _downloadController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: AnimatedBuilder(
        animation: PlayerController.instance,
        builder: (context, _) {
          return Stack(
            children: [
              Positioned.fill(child: ThemeBackground()),
              Positioned.fill(
                child: Padding(
                  padding: const EdgeInsets.only(bottom: 78),
                  child: _activeTab(),
                ),
              ),
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: _tabBar(),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _activeTab() {
    switch (_selected) {
      case Tab.search:
        return SearchScreen(controller: _searchController);
      case Tab.library:
        return LibraryScreen(controller: _libraryController);
      case Tab.lyrics:
        return LyricsScreen(isActive: _selected == Tab.lyrics);
      case Tab.player:
        return const PlayerScreen();
      case Tab.download:
        return DownloadScreen(controller: _downloadController);
    }
  }

  Widget _tabBar() {
    final player = PlayerController.instance;
    return Container(
      padding: EdgeInsets.only(
        top: 10,
        bottom: MediaQuery.of(context).padding.bottom + 12,
      ),
      decoration: BoxDecoration(
        color: player.theme.backgroundBottom,
        border: Border(
          top: BorderSide(color: Colors.white.withValues(alpha: 0.08), width: 0.5),
        ),
      ),
      child: Row(
        children: Tab.values.map((tab) {
          final isSelected = _selected == tab;
          return Expanded(
            child: InkWell(
              onTap: () {
                setState(() => _selected = tab);
              },
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    tab.icon,
                    size: 20,
                    color: isSelected
                        ? player.theme.accent
                        : Colors.white.withValues(alpha: 0.4),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    tab.label,
                    style: TextStyle(
                      fontSize: 9,
                      fontWeight:
                          isSelected ? FontWeight.w600 : FontWeight.w400,
                      color: isSelected
                          ? player.theme.accent
                          : Colors.white.withValues(alpha: 0.4),
                    ),
                  ),
                ],
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}