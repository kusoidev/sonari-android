import 'dart:io';

import 'package:path_provider/path_provider.dart';

/// A single audio track. Mirrors the iOS Track model field-for-field so the
/// library.json written by either platform decodes on the other where it can.
class Track {
  final String id;
  String title;
  final String youtubeID;
  final String? thumbnailURL;
  final String? artist;
  final double? duration;
  final String? fileName;
  final int? fileSize;
  final DateTime addedAt;
  final List<String> playlistIDs;
  final String? streamingURL;
  int playCount;

  Track({
    String? id,
    required this.title,
    required this.youtubeID,
    this.thumbnailURL,
    this.artist,
    this.duration,
    this.fileName,
    this.fileSize,
    DateTime? addedAt,
    List<String>? playlistIDs,
    this.streamingURL,
    this.playCount = 0,
  })  : id = id ?? _uuid(),
        addedAt = addedAt ?? DateTime.now(),
        playlistIDs = playlistIDs ?? [];

  /// Local file URL if the track was downloaded, resolving against Documents.
  Future<File?> localFile() async {
    if (fileName == null) return null;
    final docs = await getApplicationDocumentsDirectory();
    return File('${docs.path}/$fileName');
  }

  /// Synchronous variant for callers that already resolved the docs dir.
  File? localFileSync(String docsPath) {
    if (fileName == null) return null;
    return File('$docsPath/$fileName');
  }

  String get formattedDuration {
    final d = duration;
    if (d == null) return '--:--';
    final mins = d ~/ 60;
    final secs = d.toInt() % 60;
    return '$mins:${secs.toString().padLeft(2, '0')}';
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'youtubeID': youtubeID,
        'thumbnailURL': thumbnailURL,
        'artist': artist,
        'duration': duration,
        'fileName': fileName,
        'fileSize': fileSize,
        'addedAt': addedAt.millisecondsSinceEpoch / 1000.0,
        'playlistIDs': playlistIDs,
        'streamingURL': streamingURL,
        'playCount': playCount,
      };

  factory Track.fromJson(Map<String, dynamic> json) {
    // addedAt may be a double (seconds since epoch) from iOS or a num here.
    DateTime added;
    final rawAdded = json['addedAt'];
    if (rawAdded is num) {
      added = DateTime.fromMillisecondsSinceEpoch((rawAdded * 1000).round());
    } else {
      added = DateTime.now();
    }

    return Track(
      id: json['id'] as String?,
      title: json['title'] as String? ?? 'Unknown',
      youtubeID: json['youtubeID'] as String? ?? '',
      thumbnailURL: json['thumbnailURL'] as String?,
      artist: json['artist'] as String?,
      duration: (json['duration'] as num?)?.toDouble(),
      fileName: json['fileName'] as String?,
      fileSize: (json['fileSize'] as num?)?.toInt(),
      addedAt: added,
      playlistIDs: (json['playlistIDs'] as List?)?.cast<String>() ?? [],
      streamingURL: json['streamingURL'] as String?,
      playCount: (json['playCount'] as num?)?.toInt() ?? 0,
    );
  }

  Track copyWith({
    String? title,
    int? playCount,
    List<String>? playlistIDs,
  }) {
    return Track(
      id: id,
      title: title ?? this.title,
      youtubeID: youtubeID,
      thumbnailURL: thumbnailURL,
      artist: artist,
      duration: duration,
      fileName: fileName,
      fileSize: fileSize,
      addedAt: addedAt,
      playlistIDs: playlistIDs ?? this.playlistIDs,
      streamingURL: streamingURL,
      playCount: playCount ?? this.playCount,
    );
  }

  static String _uuid() {
    // Simple time-based id; uuid package is used elsewhere where a real v4 is wanted.
    return '${DateTime.now().microsecondsSinceEpoch}-${_rand()}';
  }

  static int _rand() => DateTime.now().hashCode & 0xffffff;
}

/// A user playlist. trackIDs is the source of truth; Track.playlistIDs is the
/// denormalized reverse index kept in sync by LibraryManager.
class Playlist {
  final String id;
  String name;
  final List<String> trackIDs;
  final DateTime createdAt;

  Playlist({
    String? id,
    required this.name,
    List<String>? trackIDs,
    DateTime? createdAt,
  })  : id = id ?? Track._uuid(),
        trackIDs = trackIDs ?? [],
        createdAt = createdAt ?? DateTime.now();

  void addTrack(String trackID) {
    if (!trackIDs.contains(trackID)) trackIDs.add(trackID);
  }

  void removeTrack(String trackID) {
    trackIDs.removeWhere((id) => id == trackID);
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'trackIDs': trackIDs,
        'createdAt': createdAt.millisecondsSinceEpoch / 1000.0,
      };

  factory Playlist.fromJson(Map<String, dynamic> json) {
    DateTime created;
    final raw = json['createdAt'];
    if (raw is num) {
      created = DateTime.fromMillisecondsSinceEpoch((raw * 1000).round());
    } else {
      created = DateTime.now();
    }
    return Playlist(
      id: json['id'] as String?,
      name: json['name'] as String? ?? 'Playlist',
      trackIDs: (json['trackIDs'] as List?)?.cast<String>() ?? [],
      createdAt: created,
    );
  }
}