import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;

/// Dynamic theme derived from the currently playing track's artwork.
/// Only color math runs here - the result is four colors that tint the app.
class AppTheme {
  final Color backgroundTop;
  final Color backgroundBottom;
  final Color accent;
  final Color onAccent;

  const AppTheme({
    required this.backgroundTop,
    required this.backgroundBottom,
    required this.accent,
    required this.onAccent,
  });

  static const AppTheme dark = AppTheme(
    backgroundTop: Color.fromARGB(255, 20, 20, 23),
    backgroundBottom: Color.fromARGB(255, 8, 8, 10),
    accent: Colors.white,
    onAccent: Colors.black,
  );

  /// Builds a theme from raw image bytes. Runs the saturation-weighted average
  /// color pass and derives a background gradient plus an accent.
  static AppTheme fromBytes(Uint8List bytes) {
    final avg = _averageColor(bytes);
    if (avg == null) return dark;

    final hsl = HSLColor.fromColor(avg);
    final hue = hsl.hue;
    final saturation = hsl.saturation;
    final brightness = hsl.lightness;

    // Accent: pump saturation enough to read as a real color, keep it mid-bright.
    final accentSaturation = saturation < 0.45 ? 0.45 : saturation;
    final accent = HSLColor.fromAHSL(
      1,
      hue,
      accentSaturation,
      brightness < 0.55 ? 0.55 : brightness,
    ).toColor();

    // Background top: same hue, lower saturation, dark enough for white text.
    final topSaturation = (saturation * 0.55) < 0.12 ? 0.12 : saturation * 0.55;
    final top = HSLColor.fromAHSL(1, hue, topSaturation, 0.22).toColor();

    // Background bottom: near-black with a hint of the hue.
    final bottom = HSLColor.fromAHSL(1, hue, topSaturation * 0.4, 0.06).toColor();

    return AppTheme(
      backgroundTop: top,
      backgroundBottom: bottom,
      accent: accent,
      onAccent: _bestForeground(accent),
    );
  }

  /// Static entry point so the color math can run in a background isolate via
  /// compute(), keeping the main thread free when a song starts.
  static AppTheme fromBytesSync(Uint8List bytes) => fromBytes(bytes);

  /// Returns black or white, whichever contrasts better with the accent.
  static Color _bestForeground(Color color) {
    return _relativeLuminance(color) > 0.55 ? Colors.black : Colors.white;
  }

  static double _relativeLuminance(Color color) {
    double linear(double c) {
      return c <= 0.03928
          ? c / 12.92
          : math.pow((c + 0.055) / 1.055, 2.4).toDouble();
    }

    return 0.2126 * linear(color.r) +
        0.7152 * linear(color.g) +
        0.0722 * linear(color.b);
  }

  /// Saturation-weighted average color. Vivid pixels dominate, gray pixels
  /// barely count - same weighting the iOS app used.
  static Color? _averageColor(Uint8List bytes) {
    final decoded = img.decodeImage(bytes);
    if (decoded == null) return null;

    final resized = img.copyResize(decoded, width: 24, height: 24);
    double weightedR = 0;
    double weightedG = 0;
    double weightedB = 0;
    double totalWeight = 0;

    for (final pixel in resized) {
      final r = pixel.r.toDouble();
      final g = pixel.g.toDouble();
      final b = pixel.b.toDouble();
      final a = pixel.a / 255.0;
      if (a <= 0.05) continue;

      final maxC = math.max(r, math.max(g, b));
      final minC = math.min(r, math.min(g, b));
      if (maxC <= 0) continue;

      final saturation = (maxC - minC) / maxC;
      final weight = saturation * saturation + 0.05;

      weightedR += r * weight;
      weightedG += g * weight;
      weightedB += b * weight;
      totalWeight += weight;
    }

    if (totalWeight <= 0) return null;
    return Color.fromARGB(
      255,
      (weightedR / totalWeight).round(),
      (weightedG / totalWeight).round(),
      (weightedB / totalWeight).round(),
    );
  }
}