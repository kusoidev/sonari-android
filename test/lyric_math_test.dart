import 'package:flutter_test/flutter_test.dart';
import 'package:sonari/models/lyric_line.dart';
import 'package:sonari/state/player_controller.dart';

List<LyricLine> _lines(List<String> texts, double gap) {
  return [
    for (var i = 0; i < texts.length; i++)
      LyricLine(
        time: i * gap,
        text: texts[i],
        startTime: i * gap,
        endTime: (i + 1) * gap,
      ),
  ];
}

void main() {
  group('LyricMath.revealedFraction', () {
    test('stays within 0..1 across the whole line', () {
      final lyrics = _lines(['hello world this is a line'], 4);
      for (double t = -1; t < 6; t += 0.1) {
        final f = LyricMath.revealedFraction(lyrics, 0, t);
        expect(f, greaterThanOrEqualTo(0.0));
        expect(f, lessThanOrEqualTo(1.0));
      }
    });

    test('is monotonic non-decreasing over the line', () {
      final lyrics = _lines(['a fairly long line of lyric text here'], 4);
      double prev = -1;
      for (double t = 0; t <= 4; t += 0.05) {
        final f = LyricMath.revealedFraction(lyrics, 0, t);
        expect(f, greaterThanOrEqualTo(prev - 1e-9));
        prev = f;
      }
    });

    test('reaches full reveal by the end of the line', () {
      final lyrics = _lines(['short line'], 4);
      final f = LyricMath.revealedFraction(lyrics, 0, 4.0);
      expect(f, closeTo(1.0, 0.001));
    });

    test('returns 0 for out-of-range index', () {
      final lyrics = _lines(['only line'], 4);
      expect(LyricMath.revealedFraction(lyrics, -1, 1), 0);
      expect(LyricMath.revealedFraction(lyrics, 5, 1), 0);
    });
  });

  group('LyricMath.isLineCurrent', () {
    test('switches exactly on boundaries', () {
      final lyrics = _lines(['one', 'two', 'three'], 2);
      expect(LyricMath.isLineCurrent(lyrics, 0, -0.1), isFalse);
      expect(LyricMath.isLineCurrent(lyrics, 0, 0.0), isTrue);
      expect(LyricMath.isLineCurrent(lyrics, 0, 1.99), isTrue);
      expect(LyricMath.isLineCurrent(lyrics, 0, 2.0), isFalse);
      expect(LyricMath.isLineCurrent(lyrics, 1, 2.0), isTrue);
      expect(LyricMath.isLineCurrent(lyrics, 2, 4.0), isTrue);
      expect(LyricMath.isLineCurrent(lyrics, 2, 999.0), isTrue);
    });
  });

  group('LyricMath.classify', () {
    test('returns one of fast/normal/slow', () {
      final lyrics = _lines([
        'This is a very very long line that takes a while',
        'short',
        'This is a very very long line that takes a while again',
        'short',
      ], 4);
      for (var i = 0; i < lyrics.length; i++) {
        final cls = LyricMath.classify(lyrics, i);
        expect(['fast', 'normal', 'slow'], contains(cls));
      }
    });

    test('handles an empty lyrics list safely', () {
      expect(LyricMath.classify([], 0), 'normal');
    });
  });
}
