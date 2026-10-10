import 'dart:async';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

import '../models/track.dart';
import 'api_service.dart';
import 'library_manager.dart';

enum JobState { queued, preparing, downloading, completed, failed }

extension JobStateLabel on JobState {
  String get label {
    switch (this) {
      case JobState.queued:
        return 'Queued';
      case JobState.preparing:
        return 'Preparing';
      case JobState.downloading:
        return 'Downloading';
      case JobState.completed:
        return 'Saved';
      case JobState.failed:
        return 'Failed';
    }
  }

  bool get isFinished =>
      this == JobState.completed || this == JobState.failed;
}

class DownloadJob {
  final String id;
  final String videoID;
  final String title;
  final String? thumbnailURL;
  final String? artist;
  JobState state;
  double progress;
  String? error;

  DownloadJob({
    required this.id,
    required this.videoID,
    required this.title,
    this.thumbnailURL,
    this.artist,
    this.state = JobState.queued,
    this.progress = 0,
    this.error,
  });
}

/// Download queue plus direct fetch-and-save. Jobs run serially, one at a
/// time. Port of the iOS DownloadService.
class DownloadService extends ChangeNotifier {
  DownloadService._();
  static final DownloadService instance = DownloadService._();

  final List<DownloadJob> jobs = [];
  bool _processing = false;

  /// Queue a track for download.
  void enqueue({
    required String videoID,
    required String title,
    String? thumbnailURL,
    String? artist,
  }) {
    if (LibraryManager.instance.tracks.any((t) => t.youtubeID == videoID)) {
      return;
    }
    if (jobs.any((j) =>
        j.videoID == videoID && !j.state.isFinished)) {
      return;
    }

    jobs.add(DownloadJob(
      id: _jobId(),
      videoID: videoID,
      title: title,
      thumbnailURL: thumbnailURL,
      artist: artist,
    ));
    notifyListeners();

    if (!_processing) {
      _processQueue();
    }
  }

  void removeFinished() {
    jobs.removeWhere((j) => j.state.isFinished);
    notifyListeners();
  }

  bool get hasFinishedJobs => jobs.any((j) => j.state.isFinished);

  /// Download a track directly and return the saved Track. Used by the player
  /// so playback can start as soon as the file is ready.
  Future<Track> fetchAndSave({
    required String videoID,
    required String title,
    String? thumbnailURL,
    String? artist,
    void Function(double)? onProgress,
  }) async {
    final status = await _ensureCached(videoID, title);
    final file = await _downloadFile(videoID, title, onProgress);

    final track = Track(
      title: title,
      youtubeID: videoID,
      thumbnailURL: thumbnailURL,
      artist: artist,
      fileName: file.uri.pathSegments.last,
      fileSize: status.fileSize,
    );
    LibraryManager.instance.addTrack(track);
    return track;
  }

  Future<void> _processQueue() async {
    if (_processing) return;
    _processing = true;
    try {
      while (true) {
        final idx = jobs.indexWhere((j) => j.state == JobState.queued);
        if (idx < 0) break;
        await _processJob(idx);
      }
    } finally {
      _processing = false;
    }
  }

  Future<void> _processJob(int idx) async {
    if (idx >= jobs.length) return;
    final job = jobs[idx];
    job.state = JobState.preparing;
    notifyListeners();

    try {
      final status = await _ensureCached(job.videoID, job.title);
      job.state = JobState.downloading;
      notifyListeners();

      final file = await _downloadFile(
        job.videoID,
        job.title,
        (p) {
          job.progress = p;
          notifyListeners();
        },
      );

      final track = Track(
        title: job.title,
        youtubeID: job.videoID,
        thumbnailURL: job.thumbnailURL,
        artist: job.artist,
        fileName: file.uri.pathSegments.last,
        fileSize: status.fileSize,
      );
      LibraryManager.instance.addTrack(track);

      job.state = JobState.completed;
      job.progress = 1;
      notifyListeners();
    } catch (e) {
      job.state = JobState.failed;
      job.error = e.toString();
      notifyListeners();
    }
  }

  // MARK: - Server + file transfer

  Future<StatusResponse> _ensureCached(String videoID, String title) async {
    var status = await ApiService.checkCache(videoID);
    if (status.cached) return status;

    await ApiService.requestDownload(videoID, title);

    // Poll at 500ms intervals, cap at 60s.
    for (var i = 0; i < 120; i++) {
      await Future.delayed(const Duration(milliseconds: 500));
      status = await ApiService.checkCache(videoID);
      if (status.cached) return status;
    }
    throw APIError('Download job timed out');
  }

  Future<File> _downloadFile(
    String videoID,
    String title,
    void Function(double)? onProgress,
  ) async {
    final url = ApiService.serveURL(videoID);
    if (url == null) throw APIError('Bad serve URL');

    final safeTitle = title
        .replaceAll(RegExp(r'[^\w\s\-]'), '')
        .trim()
        .replaceAll(' ', '_');
    final docs = await getApplicationDocumentsDirectory();
    final dest = File('${docs.path}/${safeTitle}_$videoID.m4a');

    await Dio().download(
      url,
      dest.path,
      onReceiveProgress: (received, total) {
        if (total > 0 && onProgress != null) {
          onProgress((received / total).clamp(0, 1).toDouble());
        }
      },
      options: Options(
        receiveTimeout: const Duration(minutes: 5),
      ),
    );
    return dest;
  }

  String _jobId() =>
      '${DateTime.now().microsecondsSinceEpoch}-${jobs.length}';
}