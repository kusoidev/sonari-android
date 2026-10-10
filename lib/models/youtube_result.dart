/// One row from a server /search response.
class YouTubeSearchResult {
  final String id;
  final String title;
  final String? thumbnailURL;
  final String? duration;
  final String? channelName;

  YouTubeSearchResult({
    required this.id,
    required this.title,
    this.thumbnailURL,
    this.duration,
    this.channelName,
  });

  factory YouTubeSearchResult.fromJson(Map<String, dynamic> json) {
    return YouTubeSearchResult(
      id: json['id'] as String? ?? '',
      title: json['title'] as String? ?? 'Unknown',
      thumbnailURL: json['thumbnailURL'] as String?,
      duration: json['duration'] as String?,
      channelName: json['channelName'] as String?,
    );
  }
}