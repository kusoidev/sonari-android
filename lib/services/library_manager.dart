import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

import '../models/track.dart';

/// Owns the on-disk library: tracks plus playlists. Singleton, loaded once,
/// saved after every mutation. Files live in the app Documents directory as
/// library.json and playlists.json, same names iOS used.
class LibraryManager extends ChangeNotifier {
  LibraryManager._();
  static final LibraryManager instance = LibraryManager._();

  List<Track> tracks = [];
  List<Playlist> playlists = [];

  String? _docsPath;
  File? _tracksFile;
  File? _playlistsFile;
  bool _loaded = false;

  Future<void> load() async {
    if (_loaded) return;
    final docs = await getApplicationDocumentsDirectory();
    _docsPath = docs.path;
    _tracksFile = File('${docs.path}/library.json');
    _playlistsFile = File('${docs.path}/playlists.json');
    await _loadTracks();
    await _loadPlaylists();
    _loaded = true;
    notifyListeners();
  }

  String get docsPath => _docsPath ?? '';

  void _saveTracks() {
    final file = _tracksFile;
    if (file == null) return;
    final data = jsonEncode(tracks.map((t) => t.toJson()).toList());
    file.writeAsStringSync(data);
  }

  void _savePlaylists() {
    final file = _playlistsFile;
    if (file == null) return;
    final data = jsonEncode(playlists.map((p) => p.toJson()).toList());
    file.writeAsStringSync(data);
  }

  Future<void> _loadTracks() async {
    final file = _tracksFile;
    if (file == null || !file.existsSync()) return;
    try {
      final raw = await file.readAsString();
      final list = jsonDecode(raw) as List;
      tracks = list
          .map((e) => Track.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (_) {
      tracks = [];
    }
  }

  Future<void> _loadPlaylists() async {
    final file = _playlistsFile;
    if (file == null || !file.existsSync()) return;
    try {
      final raw = await file.readAsString();
      final list = jsonDecode(raw) as List;
      playlists = list
          .map((e) => Playlist.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (_) {
      playlists = [];
    }
  }

  // MARK: - Tracks

  void addTrack(Track track) {
    final idx = tracks.indexWhere((t) => t.id == track.id);
    if (idx >= 0) {
      tracks[idx] = track;
    } else {
      tracks.add(track);
    }
    _saveTracks();
    notifyListeners();
  }

  void renameTrack(Track track, String newTitle) {
    final idx = tracks.indexWhere((t) => t.id == track.id);
    if (idx < 0) return;
    tracks[idx].title = newTitle;
    _saveTracks();
    notifyListeners();
  }

  void removeTrack(Track track) {
    final file = track.localFileSync(_docsPath ?? '');
    if (file != null && file.existsSync()) {
      try {
        file.deleteSync();
      } catch (_) {}
    }
    tracks.removeWhere((t) => t.id == track.id);

    for (final p in playlists) {
      p.removeTrack(track.id);
    }
    _saveTracks();
    _savePlaylists();
    notifyListeners();
  }

  Track? track(String id) {
    for (final t in tracks) {
      if (t.id == id) return t;
    }
    return null;
  }

  /// Increment play count so most-played ranking works.
  void recordPlay(Track track) {
    final idx = tracks.indexWhere((t) => t.id == track.id);
    if (idx < 0) return;
    tracks[idx].playCount += 1;
    _saveTracks();
    notifyListeners();
  }

  /// Top N most played tracks, sorted by play count descending.
  List<Track> mostPlayed({int limit = 5}) {
    final played = tracks.where((t) => t.playCount > 0).toList();
    played.sort((a, b) {
      final byCount = b.playCount.compareTo(a.playCount);
      if (byCount != 0) return byCount;
      return b.addedAt.compareTo(a.addedAt);
    });
    return played.take(limit).toList();
  }

  // MARK: - Playlists

  Playlist createPlaylist(String name) {
    final playlist = Playlist(name: name);
    playlists.add(playlist);
    _savePlaylists();
    notifyListeners();
    return playlist;
  }

  void deletePlaylist(Playlist playlist) {
    playlists.removeWhere((p) => p.id == playlist.id);
    for (final t in tracks) {
      t.playlistIDs.removeWhere((id) => id == playlist.id);
    }
    _savePlaylists();
    _saveTracks();
    notifyListeners();
  }

  void renamePlaylist(Playlist playlist, String newName) {
    final idx = playlists.indexWhere((p) => p.id == playlist.id);
    if (idx < 0) return;
    playlists[idx].name = newName;
    _savePlaylists();
    notifyListeners();
  }

  void addTrackToPlaylist(Track track, Playlist playlist) {
    final pIdx = playlists.indexWhere((p) => p.id == playlist.id);
    if (pIdx < 0) return;
    playlists[pIdx].addTrack(track.id);

    final tIdx = tracks.indexWhere((t) => t.id == track.id);
    if (tIdx >= 0 && !tracks[tIdx].playlistIDs.contains(playlist.id)) {
      tracks[tIdx].playlistIDs.add(playlist.id);
    }
    _savePlaylists();
    _saveTracks();
    notifyListeners();
  }

  void removeTrackFromPlaylist(Track track, Playlist playlist) {
    final pIdx = playlists.indexWhere((p) => p.id == playlist.id);
    if (pIdx >= 0) playlists[pIdx].removeTrack(track.id);

    final tIdx = tracks.indexWhere((t) => t.id == track.id);
    if (tIdx >= 0) {
      tracks[tIdx].playlistIDs.removeWhere((id) => id == playlist.id);
    }
    _savePlaylists();
    _saveTracks();
    notifyListeners();
  }

  List<Track> tracksInPlaylist(Playlist playlist) {
    return tracks.where((t) => playlist.trackIDs.contains(t.id)).toList();
  }
}