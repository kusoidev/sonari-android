import 'package:flutter/foundation.dart';

import '../services/download_service.dart';
import '../services/library_manager.dart';
import '../services/youtube_extractor.dart';

/// Drives the Download tab: a pasted URL plus the shared job queue.
class DownloadController extends ChangeNotifier {
  String urlInput = '';

  List<DownloadJob> get jobs => DownloadService.instance.jobs;
  bool get hasFinishedJobs => DownloadService.instance.hasFinishedJobs;

  void setUrlInput(String value) {
    urlInput = value;
    notifyListeners();
  }

  /// Parse the pasted URL and enqueue it.
  void pasteAndDownload() {
    if (urlInput.isEmpty) return;
    final videoID = YouTubeExtractor.extractVideoID(urlInput);
    if (videoID == null) return;

    if (LibraryManager.instance.tracks.any((t) => t.youtubeID == videoID)) {
      return;
    }

    DownloadService.instance.enqueue(
      videoID: videoID,
      title: 'track_$videoID',
    );
    urlInput = '';
    notifyListeners();
  }

  void removeFinished() {
    DownloadService.instance.removeFinished();
    notifyListeners();
  }
}