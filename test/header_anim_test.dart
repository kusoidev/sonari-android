import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// Guards the player header animation. Mirrors the real structure: an
/// AnimatedSize wrapping a fixed-height SizedBox, with an AnimatedPositioned
/// child whose size is driven by an AnimatedContainer. The point of this test
/// is that the motion is still clearly in progress at the halfway mark, so a
/// front-loaded curve (which read as an instant snap) can never come back.
class Harness extends StatefulWidget {
  const Harness({super.key});
  @override
  State<Harness> createState() => _HarnessState();
}

class _HarnessState extends State<Harness> {
  bool showing = false;

  static const _dur = Duration(milliseconds: 380);
  static const _curve = Curves.easeInOutCubic;

  @override
  Widget build(BuildContext context) {
    const width = 400.0;
    final fullSide = (width - 64).clamp(0, 380).toDouble();
    final side = showing ? 56.0 : fullSide;
    final artLeft = showing ? 20.0 : (width - fullSide) / 2;
    final headerHeight = showing ? 80.0 : fullSide + 84;

    return MaterialApp(
      home: Scaffold(
        body: Column(
          children: [
            AnimatedSize(
              duration: _dur,
              curve: _curve,
              alignment: Alignment.topCenter,
              child: SizedBox(
                width: width,
                height: headerHeight,
                child: Stack(
                  clipBehavior: Clip.none,
                  children: [
                    AnimatedPositioned(
                      duration: _dur,
                      curve: _curve,
                      left: artLeft,
                      top: 0,
                      child: GestureDetector(
                        onTap: () => setState(() => showing = !showing),
                        child: AnimatedContainer(
                          key: const ValueKey('box'),
                          duration: _dur,
                          curve: _curve,
                          width: side,
                          height: side,
                          color: Colors.blue,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

void main() {
  testWidgets('artwork is still mid-travel at the halfway point',
      (tester) async {
    await tester.pumpWidget(const Harness());

    final startLeft = tester.getTopLeft(find.byKey(const ValueKey('box'))).dx;
    expect(startLeft, closeTo((400 - 336) / 2, 0.5));

    await tester.tap(find.byKey(const ValueKey('box')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 190)); // halfway

    final midLeft = tester.getTopLeft(find.byKey(const ValueKey('box'))).dx;
    final midSize = tester.getSize(find.byKey(const ValueKey('box'))).width;

    // easeInOutCubic is ~50% at halfway. The box must have moved off its start
    // and still be well short of both its final position and final size.
    expect(midLeft, lessThan(startLeft - 2));
    expect(midLeft, greaterThan(21));
    expect(midSize, greaterThan(150));
    expect(midSize, lessThan(320));

    await tester.pumpAndSettle();
    expect(
      tester.getTopLeft(find.byKey(const ValueKey('box'))).dx,
      closeTo(20, 0.5),
    );
    expect(
      tester.getSize(find.byKey(const ValueKey('box'))).width,
      closeTo(56, 0.5),
    );
  });
}
