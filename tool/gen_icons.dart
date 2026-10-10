import 'dart:io';

import 'package:image/image.dart' as img;

// Regenerates the launcher icons from the iOS 1024 source as clean 8-bit RGBA
// PNGs. sips output failed aapt2 in release, so we re-encode with the image
// package to guarantee a standard PNG aapt2 accepts.
void main() {
  const src = '/Users/kusoi/ytlocal/Sonari/Assets.xcassets/AppIcon.appiconset/icon-1024.png';
  final base = '/Users/kusoi/ytlocal/android/android/app/src/main/res';
  final densities = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192,
  };

  final bytes = File(src).readAsBytesSync();
  final decoded = img.decodeImage(bytes);
  if (decoded == null) {
    stderr.writeln('failed to decode source');
    exit(1);
  }

  densities.forEach((dens, size) {
    final resized = img.copyResize(
      decoded,
      width: size,
      height: size,
      interpolation: img.Interpolation.cubic,
    );
    final outPath = '$base/mipmap-$dens/ic_launcher.png';
    final encoded = img.encodePng(resized, level: 6);
    File(outPath).writeAsBytesSync(encoded);
    stdout.writeln('wrote $outPath (${encoded.length} bytes)');
  });
}