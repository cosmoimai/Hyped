import 'package:hyped/core/presentation/countdown_mark.dart';
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_providers.dart';

class OnboardingScreen extends ConsumerStatefulWidget {
  const OnboardingScreen({super.key});
  @override
  ConsumerState<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends ConsumerState<OnboardingScreen> {
  late final DateTime _event = DateTime.now().add(
    const Duration(days: 12, hours: 4, minutes: 30),
  );
  late final Timer _timer;
  DateTime _now = DateTime.now();
  int _theme = 0;
  static const _colors = [
    Color(0xFF6C8CFF),
    Color(0xFF263B70),
    Color(0xFFB44F38),
  ];
  static const _names = ['Soft blue', 'Midnight', 'Sunset'];
  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() => _now = DateTime.now());
    });
  }

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final pending = ref.watch(pendingInviteControllerProvider).intent;
    final duration = _event.difference(_now);
    final seconds = duration.inSeconds.clamp(0, 999999999);
    final days = seconds ~/ 86400;
    final hours = (seconds ~/ 3600) % 24;
    final minutes = (seconds ~/ 60) % 60;
    final remaining = seconds % 60;
    final text = '${days}d ${hours}h ${minutes}m ${remaining}s';
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 520),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Center(child: CountdownMark(size: 64)),
                  const SizedBox(height: 12),
                  Text(
                    'Hyped!',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 32),
                  Text(
                    'Make the wait feel special',
                    style: Theme.of(context).textTheme.headlineMedium,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'Try a countdown. Pick a theme. Bring your people along.',
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 24),
                  AnimatedContainer(
                    duration: MediaQuery.disableAnimationsOf(context)
                        ? Duration.zero
                        : const Duration(milliseconds: 180),
                    padding: const EdgeInsets.all(28),
                    decoration: BoxDecoration(
                      color: _colors[_theme],
                      borderRadius: BorderRadius.circular(28),
                    ),
                    child: Column(
                      children: [
                        const Icon(
                          Icons.wb_sunny_outlined,
                          size: 40,
                          color: Colors.white,
                        ),
                        const SizedBox(height: 16),
                        Text(
                          'Summer trip',
                          style: Theme.of(context).textTheme.headlineSmall
                              ?.copyWith(color: Colors.white),
                        ),
                        const SizedBox(height: 20),
                        Semantics(
                          label:
                              'Sample countdown: $days days, $hours hours, $minutes minutes, $remaining seconds',
                          child: ExcludeSemantics(
                            child: Text(
                              text,
                              textAlign: TextAlign.center,
                              style: Theme.of(context).textTheme.headlineMedium
                                  ?.copyWith(
                                    color: Colors.white,
                                    fontWeight: FontWeight.w800,
                                  ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        const Text(
                          'A live demo. No room is created.',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: Colors.white),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  Wrap(
                    alignment: WrapAlignment.center,
                    spacing: 8,
                    children: List.generate(
                      _names.length,
                      (index) => ChoiceChip(
                        label: Text(_names[index]),
                        selected: _theme == index,
                        onSelected: (_) => setState(() => _theme = index),
                      ),
                    ),
                  ),
                  const SizedBox(height: 28),
                  FilledButton(
                    onPressed: () => context.go('/sign-in'),
                    child: const Text('Continue'),
                  ),
                  if (pending != null)
                    TextButton(
                      onPressed: () => context.go(pending.location),
                      child: const Text('Skip to invite'),
                    ),
                  TextButton(
                    onPressed: () => context.push('/legal/terms'),
                    child: const Text('Terms of Service'),
                  ),
                  TextButton(
                    onPressed: () => context.push('/legal/privacy'),
                    child: const Text('Privacy Policy'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
