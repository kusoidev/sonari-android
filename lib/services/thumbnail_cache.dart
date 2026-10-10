import 'dart:io';

import 'package:dio/dio.dart';
import 'package:path_provider/path_provider.dart';

/// Disk cache for YouTube thumbnails keyed by youtubeID. Downloads once,
/// serves from disk after that. Mirrors the iOS ThumbnailCache behavior,
/// including in-flight de-duplication.
class ThumbnailCache {
  ThumbnailCache._();
  static final ThumbnailCache instance = ThumbnailCache._();

  Directory? _cacheDir;
  final Map<String, Future<File?>> _active = {};

  Future<Directory> _ensureDir() async {
    if (_cacheDir != null) return _cacheDir!;
    final docs = await getApplicationDocumentsDirectory();
    final dir = Directory('${docs.path}/thumbnails');
    if (!dir.existsSync()) dir.createSync(recursive: true);
    _cacheDir = dir;
    return dir;
  }

  /// Returns a local file for the thumbnail if it already exists on disk.
  Future<File?> cachedFile(String youtubeID) async {
    final dir = await _ensureDir();
    final file = File('${dir.path}/$youtubeID.jpg');
    return file.existsSync() ? file : null;
  }

  /// Ensures the thumbnail is cached. De-duplicates concurrent requests.
  Future<File?> ensureCached(String youtubeID, String remoteURL) async {
    final existing = await cachedFile(youtubeID);
    if (existing != null) return existing;

    if (_active.containsKey(youtubeID)) {
      return _active[youtubeID];
    }

    final task = _download(youtubeID, remoteURL);
    _active[youtubeID] = task;
    try {
      return await task;
    } finally {
      _active.remove(youtubeID);
    }
  }

  Future<File?> _download(String youtubeID, String remoteURL) async {
    try {
      final dir = await _ensureDir();
      final res = await Dio().get<List<int>>(
        remoteURL,
        options: Options(responseType: ResponseType.bytes),
      );
      final bytes = res.data;
      if (bytes == null || bytes.isEmpty) return null;
      final dest = File('${dir.path}/$youtubeID.jpg');
      await dest.writeAsBytes(bytes, flush: true);
      return dest;
    } catch (_) {
      return null;
    }
  }

  /// Fire-and-forget preload for a track's thumbnail.
  void preload(String youtubeID, String? remoteURL) {
    if (remoteURL == null) return;
    ensureCached(youtubeID, remoteURL);
  }
}