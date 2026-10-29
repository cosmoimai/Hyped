import 'dart:async';
import 'package:flutter/material.dart';

/// Device-clock display only; ticking never requests server data.
class CountdownReadout extends StatefulWidget {
  const CountdownReadout({
    required this.eventAt,
    this.onDark = false,
    this.clock,
    super.key,
  });
  final DateTime eventAt;
  final bool onDark;
  final DateTime Function()? clock;

  @override
  State<CountdownReadout> createState() => _CountdownReadoutState();
}

class _CountdownReadoutState extends State<CountdownReadout>
    with WidgetsBindingObserver {
  Timer? _timer;
  late DateTime _now;

  void _start() {
    _timer?.cancel();
    _now = (widget.clock ?? DateTime.now)();
    if (widget.eventAt.isAfter(_now)) {
      _timer = Timer.periodic(const Duration(seconds: 1), (_) => _tick());
    }
  }

  void _tick() {
    setState(() => _now = (widget.clock ?? DateTime.now)());
    if (!widget.eventAt.isAfter(_now)) _timer?.cancel();
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _start();
  }

  @override
  void didUpdateWidget(CountdownReadout oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.eventAt != widget.eventAt ||
        oldWidget.clock != widget.clock) {
      _start();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) setState(_start);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final remaining = widget.eventAt.difference(_now);
    final foreground = widget.onDark
        ? Colors.white
        : Theme.of(context).colorScheme.onSurface;
    if (remaining <= Duration.zero) {
      return Text(
        'Event started',
        textAlign: TextAlign.center,
        style: Theme.of(
          context,
        ).textTheme.headlineSmall?.copyWith(color: foreground),
      );
    }
    // Round up so the final fraction of a second never displays a false zero.
    final seconds = (remaining.inMicroseconds / Duration.microsecondsPerSecond)
        .ceil();
    final values = [
      seconds ~/ 86400,
      seconds ~/ 3600 % 24,
      seconds ~/ 60 % 60,
      seconds % 60,
    ];
    const labels = ['DAYS', 'HOURS', 'MINS', 'SECS'];
    return Semantics(
      label:
          '${values[0]} days, ${values[1]} hours, ${values[2]} minutes, ${values[3]} seconds remaining',
      child: ExcludeSemantics(
        child: LayoutBuilder(
          builder: (context, constraints) {
            final columns =
                constraints.maxWidth < 260 ||
                    MediaQuery.textScalerOf(context).scale(14) > 21
                ? 2
                : 4;
            final width = (constraints.maxWidth - (columns - 1) * 8) / columns;
            return Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (var i = 0; i < values.length; i++)
                  Container(
                    width: width,
                    padding: const EdgeInsets.symmetric(
                      vertical: 14,
                      horizontal: 6,
                    ),
                    decoration: BoxDecoration(
                      color: widget.onDark
                          ? Colors.white.withValues(alpha: .13)
                          : Theme.of(context).colorScheme.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        FittedBox(
                          fit: BoxFit.scaleDown,
                          child: Text(
                            values[i].toString().padLeft(2, '0'),
                            style: Theme.of(context).textTheme.headlineMedium
                                ?.copyWith(
                                  color: foreground,
                                  fontWeight: FontWeight.w600,
                                  fontFeatures: const [
                                    FontFeature.tabularFigures(),
                                  ],
                                ),
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          labels[i],
                          style: TextStyle(
                            fontSize: 12,
                            letterSpacing: .4,
                            fontWeight: FontWeight.w600,
                            color: foreground,
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
            );
          },
        ),
      ),
    );
  }
}
