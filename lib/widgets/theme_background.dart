import 'package:flutter/material.dart';

import '../state/player_controller.dart';
import '../theme/app_theme.dart';

/// The full-bleed gradient behind every screen. Tints to the current track's
/// artwork and eases to the new palette on track change.
///
/// AnimatedContainer does not tween a LinearGradient's colors (the whole
/// decoration swaps in a single frame), so this widget drives the two stops
/// with an AnimationController and ColorTween and rebuilds the gradient each
/// frame, giving a real cross-fade between themes.
class ThemeBackground extends StatefulWidget {
  final AppTheme? theme;
  const ThemeBackground({super.key, this.theme});

  @override
  State<ThemeBackground> createState() => _ThemeBackgroundState();
}

class _ThemeBackgroundState extends State<ThemeBackground>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late ColorTween _topTween;
  late ColorTween _bottomTween;

  AppTheme get _target => widget.theme ?? PlayerController.instance.theme;

  @override
  void initState() {
    super.initState();
    final t = _target;
    _topTween = ColorTween(begin: t.backgroundTop, end: t.backgroundTop);
    _bottomTween =
        ColorTween(begin: t.backgroundBottom, end: t.backgroundBottom);
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
      value: 1,
    );
  }

  @override
  void didUpdateWidget(covariant ThemeBackground oldWidget) {
    super.didUpdateWidget(oldWidget);
    _retarget();
  }

  void _retarget() {
    final t = _target;
    final currentTop = _topTween.evaluate(_controller) ?? t.backgroundTop;
    final currentBottom =
        _bottomTween.evaluate(_controller) ?? t.backgroundBottom;
    if (currentTop.toARGB32() == t.backgroundTop.toARGB32() &&
        currentBottom.toARGB32() == t.backgroundBottom.toARGB32()) {
      return;
    }
    _topTween = ColorTween(begin: currentTop, end: t.backgroundTop);
    _bottomTween = ColorTween(begin: currentBottom, end: t.backgroundBottom);
    _controller.forward(from: 0);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        final top = _topTween.evaluate(_controller) ?? _target.backgroundTop;
        final bottom =
            _bottomTween.evaluate(_controller) ?? _target.backgroundBottom;
        return DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [top, bottom],
            ),
          ),
        );
      },
    );
  }
}
