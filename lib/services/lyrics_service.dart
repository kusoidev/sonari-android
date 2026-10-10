import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:path_provider/path_provider.dart';

import '../models/lyric_line.dart';

/// Fetches synced lyrics from lrclib, parses LRC, synthesizes timing for plain
/// lyrics, falls back to an AI title-cleaner, and caches everything on disk.
/// Direct port of the iOS LyricsService.
class LyricsService {
  static final Dio _dio = Dio(BaseOptions(
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 30),
    headers: {'User-Agent': 'Kusoinema/1.0'},
  ));

  static Directory? _cacheDir;

  static Future<Directory> _ensureCacheDir() async {
    if (_cacheDir != null) return _cacheDir!;
    final docs = await getApplicationDocumentsDirectory();
    final dir = Directory('${docs.path}/lyrics_cache');
    if (!dir.existsSync()) dir.createSync(recursive: true);
    _cacheDir = dir;
    return dir;
  }

  /// Main entry. Resolves lyrics for a track, trying cache first when allowed.
  static Future<List<LyricLine>?> fetchSynced({
    required String title,
    String? artist,
    required double duration,
    void Function(String)? onStatus,
  }) async {
    final durationSecs = duration.isFinite && duration > 0 ? duration.toInt() : 0;

    var cleanedTitle = cleanTitle(title);
    var effectiveArtist = artist;

    if ((effectiveArtist == null ||
            effectiveArtist.isEmpty ||
            effectiveArtist.toLowerCase().contains('unknown')) &&
        title.contains('-')) {
      final parts = title.split('-').map((p) => p.trim()).toList();
      if (parts.length >= 2) {
        effectiveArtist = parts[0];
        cleanedTitle = cleanTitle(parts[1]);
      }
    }

    if (effectiveArtist != null && effectiveArtist.isNotEmpty) {
      effectiveArtist = cleanArtist(effectiveArtist);
    }

    if (effectiveArtist != null && effectiveArtist.isNotEmpty) {
      cleanedTitle = cleanedTitle
          .replaceAll(RegExp(RegExp.escape(effectiveArtist), caseSensitive: false), '')
          .trim();
    }

    List<LyricLine>? resolveResult(String raw) {
      final parsed = parseLRC(raw);
      if (parsed != null) return parsed;
      final synthesized = synthesizeLyrics(raw, duration);
      return synthesized.isEmpty ? null : synthesized;
    }

    // Tier 1: exact get + exact search, raced.
    final tier1 = await _firstOf([
      () => _tryGet(cleanedTitle, effectiveArtist, durationSecs),
      () => _trySearch(cleanedTitle, effectiveArtist, cleanedTitle, durationSecs),
    ]);
    if (tier1 != null) {
      final lines = resolveResult(tier1);
      if (lines != null) return lines;
    }

    // Tier 2: free-text search, raced.
    final tier2Tasks = <Future<String?> Function()>[
      () => _trySearchFreeText(cleanedTitle, cleanedTitle, durationSecs),
    ];
    if (effectiveArtist != null) {
      tier2Tasks.add(() => _trySearchFreeText(
          '$cleanedTitle $effectiveArtist', cleanedTitle, durationSecs));
    }
    final tier2 = await _firstOf(tier2Tasks);
    if (tier2 != null) {
      final lines = resolveResult(tier2);
      if (lines != null) return lines;
    }

    onStatus?.call('Asking AI to identify song...');

    final strategies = <String>[
      '''
      You are a music database. From this YouTube video title, extract the song title and artist.
      Reply ONLY with JSON: {"artist":"...","title":"..."}

      Video title: $title
      ''',
      '''
      Given this YouTube video title, identify the song. Return ONLY:
      {"song":"...","artist":"..."}

      Title: $title
      ''',
      '''
      Clean this YouTube title to just the song name and artist. Strip all the junk.
      Reply ONLY with JSON: {"artist":"...","track":"..."}

      Raw: $title
      ''',
      '''
      What artist or band performs the song in this YouTube video title?
      Reply ONLY with JSON: {"artist":"...","song":"..."}

      Video title: $title
      ''',
    ];

    final aiPairs = <({String artist, String title})>[];
    await Future.wait(strategies.map((prompt) async {
      final result = await _aiQuery(prompt);
      if (result == null) return;
      final a = result['artist'] ?? result['Artist'] ?? result['singer'] ?? '';
      final t = result['title'] ??
          result['Title'] ??
          result['song'] ??
          result['Song'] ??
          result['track'] ??
          result['Track'] ??
          result['name'] ??
          '';
      if (a.isEmpty || t.isEmpty) return;
      aiPairs.add((artist: a, title: t));
    }));

    final tried = <String>{};
    for (final pair in aiPairs) {
      final aiTitle = cleanTitle(pair.title);
      final aiArtist = pair.artist;
      final key = '$aiArtist|$aiTitle';
      if (!tried.add(key)) continue;

      onStatus?.call('Searching: $aiArtist - $aiTitle');

      final aiResult = await _firstOf([
        () => _tryGet(aiTitle, aiArtist, durationSecs),
        () => _trySearch(aiTitle, aiArtist, aiTitle, durationSecs),
      ]);
      if (aiResult != null) {
        final lines = resolveResult(aiResult);
        if (lines != null) {
          onStatus?.call('Found lyrics via AI!');
          return lines;
        }
      }
    }

    onStatus?.call('');
    return null;
  }

  static List<LyricLine> synthesizeLyrics(String text, double duration) {
    final rawLines = text
        .split('\n')
        .map((l) => l.trim())
        .where((l) => l.isNotEmpty)
        .toList();

    final lrcPattern = RegExp(r'\[\d{2}:\d{2}');
    final looksLikeLRC = rawLines.any((l) => lrcPattern.hasMatch(l));
    if (looksLikeLRC || rawLines.isEmpty || duration <= 0) return [];

    final gap = duration / rawLines.length;
    final out = <LyricLine>[];
    for (var i = 0; i < rawLines.length; i++) {
      final time = i * gap;
      out.add(LyricLine(
        time: time,
        text: rawLines[i],
        startTime: time,
        endTime: time + gap,
      ));
    }
    return out;
  }

  static Future<Map<String, String>?> _aiQuery(String prompt) async {
    try {
      final res = await Dio().post<String>(
        'https://powerbrainai.com/chat.php',
        options: Options(
          contentType: 'application/x-www-form-urlencoded',
          responseType: ResponseType.plain,
          sendTimeout: const Duration(seconds: 30),
          receiveTimeout: const Duration(seconds: 30),
        ),
        data: {
          'message': prompt,
          'messageCount': '1',
        },
      );
      final raw = res.data;
      if (raw == null) return null;

      final jsonStart = raw.indexOf('{');
      final jsonEnd = raw.lastIndexOf('}');
      if (jsonStart < 0 || jsonEnd <= jsonStart) return null;

      final jsonStr = raw.substring(jsonStart, jsonEnd + 1);
      final decoded = jsonDecode(jsonStr) as Map<String, dynamic>;
      final result = <String, String>{};
      decoded.forEach((k, v) => result[k] = '$v');
      return result;
    } catch (_) {
      return null;
    }
  }

  static String cleanTitle(String t) {
    var clean = _fixEncoding(t);

    clean = clean.replaceAll(
      RegExp(
        r'[\(\[][^\)\]]*\b(official|lyric|lyrics|video|audio|mv|clip|hd|4k|remastered?|visuali[sz]er|acoustic|cover|remix|extended|version|edit)\b.*?[\)\]]',
        caseSensitive: false,
      ),
      '',
    );

    clean = clean.replaceAll(
      RegExp(r'\s*[\(\[ ](feat\.?|ft\.?|featuring)[^\)\]]*[\)\]]',
          caseSensitive: false),
      '',
    );

    clean = clean.replaceAll(
      RegExp(r'\b(sings|covers|covered by|performed by)\b', caseSensitive: false),
      ' ',
    );

    clean = clean.replaceAll(
      RegExp(r'\s+by\s+[a-z0-9\s]+$', caseSensitive: false),
      '',
    );

    clean = clean.replaceAll(RegExp(r'[^a-z0-9\s]', caseSensitive: false), ' ');
    clean = clean.replaceAll(RegExp(r'\s+'), ' ');
    return clean.trim();
  }

  static String cleanArtist(String artist) {
    var cleaned = artist;
    cleaned = cleaned.replaceAll(RegExp(r'\s*-\s*Topic\s*$', caseSensitive: false), '');
    cleaned = cleaned.replaceAll(RegExp(r'\s*VEVO\s*$', caseSensitive: false), '');
    cleaned = cleaned.replaceAll(RegExp(r'\s*\(Official\)', caseSensitive: false), '');
    cleaned = cleaned.replaceAll(RegExp(r'\s*\[Official\]', caseSensitive: false), '');

    // Split camelCase or PascalCase artist names into separate words.
    final hasLower = RegExp(r'[a-z]').hasMatch(cleaned);
    final hasUpper = RegExp(r'[A-Z]').hasMatch(cleaned);
    if (!cleaned.contains(' ') && hasLower && hasUpper) {
      final buffer = StringBuffer();
      for (var i = 0; i < cleaned.length; i++) {
        final char = cleaned[i];
        if (RegExp(r'[A-Z]').hasMatch(char) && buffer.isNotEmpty) {
          buffer.write(' ');
        }
        buffer.write(char);
      }
      final split = buffer.toString();
      if (split.contains(' ')) cleaned = split;
    }

    return cleaned.trim();
  }

  static String _fixEncoding(String str) {
    var fixed = str;
    fixed = fixed.replaceAll('\u00E2\u0080\u009C', '"');
    fixed = fixed.replaceAll('\u00E2\u0080\u009D', '"');
    fixed = fixed.replaceAll('\u00E2\u0080\u0099', "'");
    fixed = fixed.replaceAll('\u0393\u00C7\u00A3', '"');
    fixed = fixed.replaceAll('\u0393\u00C7\u00A5', '"');
    fixed = fixed.replaceAll('\u0393\u00C7\u00A8', '"');
    fixed = fixed.replaceAll('\u0393\u00C7\u00A4', '"');
    fixed = fixed.replaceAll('\u0393\u00C7\u00A9', '"');
    fixed = fixed.replaceAll('\u0393\u00C7\u00B6', "'");
    return fixed;
  }

  static String _normalize(String s) {
    return s
        .toLowerCase()
        .replaceAll(RegExp(r'[^a-z0-9\s]'), '')
        .trim();
  }

  static double _wordOverlap(String a, String b) {
    final wa = _normalize(a)
        .split(RegExp(r'\s+'))
        .where((w) => w.length > 2)
        .toSet();
    final wb = _normalize(b)
        .split(RegExp(r'\s+'))
        .where((w) => w.length > 2)
        .toSet();
    if (wa.isEmpty) return 1;
    var matches = 0;
    for (final w in wa) {
      if (wb.contains(w)) matches++;
    }
    return matches / wa.length;
  }

  static bool _isTitleMatch(String resultTrackName, String expectedTrack) {
    final nResult = _normalize(resultTrackName);
    final nExpected = _normalize(expectedTrack);
    return nExpected.contains(nResult) ||
        _wordOverlap(expectedTrack, resultTrackName) >= 0.5;
  }

  static bool _isDurationOk(double? resultDuration, int expectedDuration) {
    if (expectedDuration <= 0 || resultDuration == null) return true;
    return (resultDuration.toInt() - expectedDuration).abs() <= 1;
  }

  static String? _pickBest(
      List<dynamic>? results, String expectedTitle, int durationSecs) {
    if (results == null || results.isEmpty) return null;

    final validated = results.where((r) {
      final m = r as Map<String, dynamic>;
      return _isTitleMatch(m['trackName'] as String? ?? '', expectedTitle);
    }).toList();
    final pool = validated.isEmpty ? results : validated;

    String? syncedWhere(bool Function(Map<String, dynamic>) test) {
      for (final r in pool) {
        final m = r as Map<String, dynamic>;
        if (m['syncedLyrics'] != null && test(m)) {
          return m['syncedLyrics'] as String;
        }
      }
      return null;
    }

    String? plainWhere(bool Function(Map<String, dynamic>) test) {
      for (final r in pool) {
        final m = r as Map<String, dynamic>;
        if (m['plainLyrics'] != null && test(m)) {
          return m['plainLyrics'] as String;
        }
      }
      return null;
    }

    final syncedOk = syncedWhere(
        (m) => _isDurationOk((m['duration'] as num?)?.toDouble(), durationSecs));
    if (syncedOk != null) return syncedOk;

    final syncedAny = syncedWhere((_) => true);
    if (syncedAny != null) return syncedAny;

    final plainOk = plainWhere(
        (m) => _isDurationOk((m['duration'] as num?)?.toDouble(), durationSecs));
    if (plainOk != null) return plainOk;

    return plainWhere((_) => true);
  }

  static Future<String?> _tryGet(
      String trackName, String? artistName, int durationSecs) async {
    if (trackName.isEmpty) return null;
    try {
      final query = <String, dynamic>{'track_name': trackName};
      if (artistName != null && artistName.isNotEmpty) {
        query['artist_name'] = artistName;
      }
      if (durationSecs > 0) query['duration'] = durationSecs;

      final res = await _dio.get('https://lrclib.net/api/get',
          queryParameters: query);
      final m = res.data as Map<String, dynamic>;
      return (m['syncedLyrics'] ?? m['plainLyrics']) as String?;
    } catch (_) {
      return null;
    }
  }

  static Future<String?> _trySearch(String trackName, String? artistName,
      String expectedTitle, int durationSecs) async {
    if (trackName.isEmpty) return null;
    try {
      final query = <String, dynamic>{'track_name': trackName};
      if (artistName != null && artistName.isNotEmpty) {
        query['artist_name'] = artistName;
      }
      final res = await _dio.get('https://lrclib.net/api/search',
          queryParameters: query);
      return _pickBest(res.data as List?, expectedTitle, durationSecs);
    } catch (_) {
      return null;
    }
  }

  static Future<String?> _trySearchFreeText(
      String query, String expectedTitle, int durationSecs) async {
    if (query.isEmpty) return null;
    try {
      final res = await _dio.get('https://lrclib.net/api/search',
          queryParameters: {'q': query});
      return _pickBest(res.data as List?, expectedTitle, durationSecs);
    } catch (_) {
      return null;
    }
  }

  /// Returns the first non-null result among the racing tasks.
  static Future<String?> _firstOf(
      List<Future<String?> Function()> tasks) async {
    if (tasks.isEmpty) return null;
    final completer = Completer<String?>();
    var remaining = tasks.length;
    for (final task in tasks) {
      task().then((result) {
        if (result != null && !completer.isCompleted) {
          completer.complete(result);
        } else {
          remaining--;
          if (remaining == 0 && !completer.isCompleted) {
            completer.complete(null);
          }
        }
      }).catchError((_) {
        remaining--;
        if (remaining == 0 && !completer.isCompleted) {
          completer.complete(null);
        }
      });
    }
    return completer.future;
  }

  static List<LyricLine>? parseLRC(String lrc) {
    final lines = <({double time, String text})>[];
    final pattern =
        RegExp(r'\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?\](.*)');

    for (final line in lrc.split('\n')) {
      final match = pattern.firstMatch(line);
      if (match == null) continue;
      final mins = double.tryParse(match.group(1) ?? '0') ?? 0;
      final secs = double.tryParse(match.group(2) ?? '0') ?? 0;
      final text = (match.group(4) ?? '').trim();
      if (text.isEmpty) continue;
      lines.add((time: mins * 60 + secs, text: text));
    }

    lines.sort((a, b) => a.time.compareTo(b.time));
    if (lines.isEmpty) return null;

    final out = <LyricLine>[];
    for (var i = 0; i < lines.length; i++) {
      final endTime = i + 1 < lines.length ? lines[i + 1].time : null;
      out.add(LyricLine(
        time: lines[i].time,
        text: lines[i].text,
        startTime: lines[i].time,
        endTime: endTime,
      ));
    }
    return out;
  }

  static Future<List<LyricLine>?> loadCachedLyrics(String youtubeID) async {
    final dir = await _ensureCacheDir();
    final file = File('${dir.path}/$youtubeID.json');
    if (!file.existsSync()) return null;
    try {
      final raw = await file.readAsString();
      final list = jsonDecode(raw) as List;
      final lines = list
          .map((e) => LyricLine.fromJson(e as Map<String, dynamic>))
          .toList();
      return lines.isEmpty ? null : lines;
    } catch (_) {
      return null;
    }
  }

  static Future<void> saveCachedLyrics(
      List<LyricLine> lines, String youtubeID) async {
    final dir = await _ensureCacheDir();
    final file = File('${dir.path}/$youtubeID.json');
    await file.writeAsString(
      jsonEncode(lines.map((l) => l.toJson()).toList()),
      flush: true,
    );
  }

  /// Synchronous save for UI-driven edits like manual lyric changes.
  static Future<void> saveCachedLyricsSync(
      List<LyricLine> lines, String youtubeID) async {
    await saveCachedLyrics(lines, youtubeID);
  }

  /// Replace a single lyric line's text in place, preserving its timings.
  static List<LyricLine> syncEditLyricLine(
      String text, int index, List<LyricLine> lines) {
    if (index < 0 || index >= lines.length || text.isEmpty) return lines;
    final updated = List<LyricLine>.from(lines);
    updated[index] = lines[index].copyWith(text: text);
    return updated;
  }

  static Future<void> deleteCachedLyrics(String youtubeID) async {
    final dir = await _ensureCacheDir();
    final file = File('${dir.path}/$youtubeID.json');
    if (file.existsSync()) {
      try {
        await file.delete();
      } catch (_) {}
    }
  }
}