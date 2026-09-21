import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hyped/app/bootstrap/providers.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) => Scaffold(
    appBar: AppBar(
      title: const Text('Hyped!'),
      actions: [
        IconButton(
          onPressed: () => ref.read(authControllerProvider).logout(),
          tooltip: 'Sign out',
          icon: const Icon(Icons.logout),
        ),
      ],
    ),
    body: Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.celebration_outlined,
              size: 72,
              color: Color(0xFF6C8CFF),
            ),
            const SizedBox(height: 20),
            Text(
              'Your countdowns will live here',
              style: Theme.of(context).textTheme.titleLarge,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            const Text('Create something worth looking forward to.'),
          ],
        ),
      ),
    ),
  );
}
