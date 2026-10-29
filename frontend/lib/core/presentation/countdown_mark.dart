import 'dart:math' as math;
import 'package:flutter/material.dart';

/// Decorative brand mark, not an event-progress indicator.
class CountdownMark extends StatelessWidget {
  const CountdownMark({this.size = 40, this.color, super.key});
  final double size;
  final Color? color;

  @override
  Widget build(BuildContext context) => ExcludeSemantics(
    child: SizedBox.square(
      dimension: size,
      child: CustomPaint(
        painter: _MarkPainter(color ?? Theme.of(context).colorScheme.primary),
      ),
    ),
  );
}

class _MarkPainter extends CustomPainter {
  _MarkPainter(this.color);
  final Color color;
  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final radius = size.width * .4;
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = size.width * .055
      ..strokeCap = StrokeCap.round;
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      -.1,
      math.pi * 1.8,
      false,
      paint,
    );
    paint
      ..color = color.withValues(alpha: .25)
      ..strokeWidth = size.width * .025;
    for (var i = 0; i < 12; i++) {
      final angle = i * math.pi / 6;
      canvas.drawLine(
        center + Offset(math.cos(angle), math.sin(angle)) * radius * .75,
        center + Offset(math.cos(angle), math.sin(angle)) * radius * .8,
        paint,
      );
    }
    final star = Offset(size.width * .52, size.height * .28);
    final path = Path()
      ..moveTo(star.dx, star.dy - size.height * .12)
      ..lineTo(star.dx + size.width * .035, star.dy - size.height * .035)
      ..lineTo(star.dx + size.width * .12, star.dy)
      ..lineTo(star.dx + size.width * .035, star.dy + size.height * .035)
      ..lineTo(star.dx, star.dy + size.height * .12)
      ..lineTo(star.dx - size.width * .035, star.dy + size.height * .035)
      ..lineTo(star.dx - size.width * .12, star.dy)
      ..lineTo(star.dx - size.width * .035, star.dy - size.height * .035)
      ..close();
    canvas.drawPath(path, Paint()..color = color);
  }

  @override
  bool shouldRepaint(_MarkPainter oldDelegate) => oldDelegate.color != color;
}
