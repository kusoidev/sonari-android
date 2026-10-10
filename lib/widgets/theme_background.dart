import 'package:flutter/material.dart';

import '../state/player_controller.dart';
import '../theme/app_theme.dart';

/// The full-bleed gradient behind every screen. Tints to the current track's
/// artwork and eases to the new palette on track change.
class ThemeBackground extends StatelessWidget {
  final AppTheme? theme;
  const ThemeBackground({super.key, this.theme});

  @override
  Widget build(BuildContext context) {
    final t = theme ?? PlayerController.instance.theme;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 600),
      curve: Curves.easeInOut,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [t.backgroundTop, t.backgroundBottom],
        ),
      ),
    );
  }
}