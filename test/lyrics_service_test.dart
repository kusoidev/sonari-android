import 'package:flutter_test/flutter_test.dart';
import 'package:sonari/models/lyric_line.dart';
import 'package:sonari/services/lyrics_service.dart';

void main() {
  group('LyricsService.parseLRC', () {
    test('honors centisecond timestamps', () {
      final lines = LyricsService.parseLRC(
        '[00:16.50] first line\n'
        '[00:21.25] second line\n'
        '[00:25.75] third line',
      );
      expect(lines, isNotNull);
      expect(lines![0].time, closeTo(16.50, 0.001));
      expect(lines[1].time, closeTo(21.25, 0.001));
      expect(lines[2].time, closeTo(25.75, 0.001));
    });

    test('honors millisecond timestamps', () {
      final lines = LyricsService.parseLRC('[00:16.500] line one');
      expect(lines, isNotNull);
      expect(lines![0].time, closeTo(16.5, 0.001));
    });

    test('still parses whole-second timestamps', () {
      final lines = LyricsService.parseLRC('[01:05] line one');
      expect(lines, isNotNull);
      expect(lines![0].time, closeTo(65.0, 0.001));
    });
  });

  group('LyricsService.isSynthesized', () {
    test('flags evenly spaced lines', () {
      final lines = [
        for (var i = 0; i < 5; i++)
          LyricLine(time: i * 3.0, text: 'line $i', startTime: i * 3.0),
      ];
      expect(LyricsService.isSynthesized(lines), isTrue);
    });

    test('passes real unevenly spaced lyrics', () {
      final lines = [
        LyricLine(time: 0, text: 'a', startTime: 0),
        LyricLine(time: 4.2, text: 'b', startTime: 4.2),
        LyricLine(time: 16.0, text: 'c', startTime: 16.0),
        LyricLine(time: 21.5, text: 'd', startTime: 21.5),
      ];
      expect(LyricsService.isSynthesized(lines), isFalse);
    });

    test('treats very short lists as real', () {
      final lines = [
        LyricLine(time: 0, text: 'a', startTime: 0),
        LyricLine(time: 3, text: 'b', startTime: 3),
      ];
      expect(LyricsService.isSynthesized(lines), isFalse);
    });
  });
}
