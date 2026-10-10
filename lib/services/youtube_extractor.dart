/// Extracts a YouTube video ID from arbitrary pasted text. Same patterns the
/// iOS app accepted, plus a bare 11-char id fallback.
class YouTubeExtractor {
  static final RegExp _urlPattern = RegExp(
    r'(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/shorts/|youtube\.com/embed/|m\.youtube\.com/watch\?v=)([a-zA-Z0-9_-]{11})',
  );

  static final RegExp _bareId = RegExp(r'^[a-zA-Z0-9_-]{11}$');

  static String? extractVideoID(String input) {
    final match = _urlPattern.firstMatch(input);
    if (match != null) return match.group(1);

    final trimmed = input.trim();
    if (_bareId.hasMatch(trimmed)) return trimmed;
    return null;
  }
}