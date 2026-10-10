import 'package:dio/dio.dart';

import '../models/youtube_result.dart';

/// Talks to the Sonari Koyeb relay. Same endpoints and auth header as iOS.
class ApiService {
  static const String baseURL =
      'https://REDACTED-HOST';
  static const String authToken = 'REDACTED';

  static final Dio _dio = Dio(BaseOptions(
    baseUrl: baseURL,
    connectTimeout: const Duration(seconds: 15),
    receiveTimeout: const Duration(seconds: 15),
    headers: {'X-Auth-Token': authToken},
  ));

  /// GET /search?q= -> list of results.
  static Future<List<YouTubeSearchResult>> search(String query) async {
    final res = await _dio.get('/search', queryParameters: {'q': query});
    final data = res.data as Map<String, dynamic>;
    final results = (data['results'] as List?) ?? [];
    return results
        .map((e) => YouTubeSearchResult.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// POST /request -> jobId. Kicks off a yt-dlp job on the worker.
  static Future<String> requestDownload(
      String videoID, String title) async {
    final res = await _dio.post('/request', data: {
      'videoId': videoID,
      'title': title,
    });
    return (res.data as Map<String, dynamic>)['jobId'] as String;
  }

  /// GET /status/:id -> cache status plus stream URL once ready.
  static Future<StatusResponse> checkCache(String videoID) async {
    final res = await _dio.get('/status/$videoID');
    return StatusResponse.fromJson(res.data as Map<String, dynamic>);
  }

  /// GET /serve/:id -> direct stream URL with the auth token appended.
  static String? serveURL(String videoID) {
    return '$baseURL/serve/$videoID?token=$authToken';
  }
}

class StatusResponse {
  final bool cached;
  final int? fileSize;
  final String? streamURL;

  StatusResponse({required this.cached, this.fileSize, this.streamURL});

  factory StatusResponse.fromJson(Map<String, dynamic> json) {
    return StatusResponse(
      cached: json['cached'] as bool? ?? false,
      fileSize: (json['fileSize'] as num?)?.toInt(),
      streamURL: json['streamURL'] as String?,
    );
  }
}

/// Thrown when a relay call fails or a download job times out.
class APIError implements Exception {
  final String message;
  APIError(this.message);
  @override
  String toString() => message;
}