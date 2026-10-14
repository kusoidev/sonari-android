import 'package:flutter/material.dart';

/// A single line of lyrics where each character is dim until the reveal edge
/// passes it, then bright. Built as one Text.rich with per-character runs, so
/// it wraps exactly like the plain line and cannot drift out of alignment.
/// Port of the iOS KaraokeText.build.
class KaraokeText extends StatelessWidget {
  final String text;
  final double revealedFraction;
  final double fontSize;
  final FontWeight weight;
  final double dimOpacity;

  const KaraokeText({
    super.key,
    required this.text,
    required this.revealedFraction,
    required this.fontSize,
    required this.weight,
    this.dimOpacity = 0.32,
  });

  @override
  Widget build(BuildContext context) {
    final chars = text.split('');
    final brightCount = chars.length * revealedFraction;
    final spans = <TextSpan>[];

    for (var i = 0; i < chars.length; i++) {
      final distance = brightCount - i;
      // Glide across ~1.2 chars so the leading edge is smooth, not stepped.
      final brightness = (distance / 1.2).clamp(0.0, 1.0);
      final opacity = dimOpacity + (1 - dimOpacity) * brightness;
      spans.add(TextSpan(
        text: chars[i],
        style: TextStyle(
          fontSize: fontSize,
          fontWeight: weight,
          color: Colors.white.withValues(alpha: opacity),
        ),
      ));
    }

    return Text.rich(
      TextSpan(children: spans),
      textAlign: TextAlign.center,
    );
  }
}
