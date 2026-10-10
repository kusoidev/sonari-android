import 'package:flutter/foundation.dart';

import '../models/track.dart';
import '../services/library_manager.dart';

enum SortOrder { dateAdded, title, duration }

extension SortOrderLabel on SortOrder {
  String get label {
    switch (this) {
      case SortOrder.dateAdded:
        return 'Date Added';
      case SortOrder.title:
        return 'Title';
      case SortOrder.duration:
        return 'Duration';
    }
  }
}

/// Drives the Library tab: sorted/filtered track list, playlists, and the
/// delete/rename flows.
class LibraryController extends ChangeNotifier {
  final LibraryManager _library = LibraryManager.instance;

  String searchText = '';
  SortOrder sortOrder = SortOrder.dateAdded;
  Playlist? selectedPlaylist;
  Track? trackToDelete;

  List<Track> get tracks => _library.tracks;
  List<Playlist> get playlists => _library.playlists;

  void load() {
    notifyListeners();
  }

  List<Track> get displayTracks {
    final base = selectedPlaylist != null
        ? _library.tracksInPlaylist(selectedPlaylist!)
        : List<Track>.from(_library.tracks);

    List<Track> sorted;
    switch (sortOrder) {
      case SortOrder.dateAdded:
        sorted = base..sort((a, b) => b.addedAt.compareTo(a.addedAt));
        break;
      case SortOrder.title:
        sorted = base
          ..sort((a, b) =>
              a.title.toLowerCase().compareTo(b.title.toLowerCase()));
        break;
      case SortOrder.duration:
        sorted = base
          ..sort((a, b) => (a.duration ?? 0).compareTo(b.duration ?? 0));
        break;
    }

    if (searchText.isEmpty) return sorted;
    final q = searchText.toLowerCase();
    return sorted.where((t) => t.title.toLowerCase().contains(q)).toList();
  }

  List<Track> get topPlayed => _library.mostPlayed(limit: 5);

  void setSearchText(String value) {
    searchText = value;
    notifyListeners();
  }

  void setSortOrder(SortOrder order) {
    sortOrder = order;
    notifyListeners();
  }

  void selectPlaylist(Playlist? playlist) {
    selectedPlaylist = playlist;
    notifyListeners();
  }

  void delete(Track track) {
    _library.removeTrack(track);
    notifyListeners();
  }

  void rename(Track track, String newTitle) {
    _library.renameTrack(track, newTitle);
    notifyListeners();
  }

  void createPlaylist(String name) {
    _library.createPlaylist(name);
    notifyListeners();
  }

  void deletePlaylist(Playlist playlist) {
    _library.deletePlaylist(playlist);
    if (selectedPlaylist?.id == playlist.id) {
      selectedPlaylist = null;
    }
    notifyListeners();
  }

  void renamePlaylist(Playlist playlist, String newName) {
    _library.renamePlaylist(playlist, newName);
    notifyListeners();
  }

  void addToPlaylist(Track track, Playlist playlist) {
    _library.addTrackToPlaylist(track, playlist);
    notifyListeners();
  }

  void removeFromPlaylist(Track track, Playlist playlist) {
    _library.removeTrackFromPlaylist(track, playlist);
    notifyListeners();
  }

  void confirmDelete(Track track) {
    trackToDelete = track;
    notifyListeners();
  }

  void cancelDelete() {
    trackToDelete = null;
    notifyListeners();
  }

  void executeDelete() {
    final track = trackToDelete;
    if (track == null) return;
    delete(track);
    trackToDelete = null;
    notifyListeners();
  }
}