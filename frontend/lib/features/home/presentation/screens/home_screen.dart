import 'package:hyped/features/notifications/presentation/push_providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_cards.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rooms = ref.watch(roomListControllerProvider);
    final push = ref.watch(pushControllerProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Hyped!'),
        actions: [
          IconButton(
            onPressed: () => context.go('/settings'),
            tooltip: 'Profile and Settings',
            icon: const Icon(Icons.person_outline),
          ),
          if (push.status.supported)
            IconButton(
              onPressed: () => context.go('/notifications'),
              tooltip: 'Notifications',
              icon: const Icon(Icons.notifications_outlined),
            ),
          IconButton(
            onPressed: () => context.go('/widgets/room'),
            tooltip: 'Countdown widget',
            icon: const Icon(Icons.widgets_outlined),
          ),
          IconButton(
            onPressed: () => ref.read(authControllerProvider).logout(),
            tooltip: 'Sign out',
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      bottomNavigationBar: push.status.supported && !push.status.enabled
          ? SafeArea(
              child: ListTile(
                title: const Text('Enable event reminders'),
                subtitle: const Text(
                  'Choose whether Hyped can send notifications.',
                ),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => context.go('/notifications'),
              ),
            )
          : null,
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.go('/rooms/new'),
        icon: const Icon(Icons.add),
        label: const Text('Create'),
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: rooms.load,
          child: rooms.loading
              ? const Center(child: CircularProgressIndicator())
              : rooms.error != null
              ? _ErrorState(message: rooms.error!, onRetry: rooms.load)
              : rooms.rooms.isEmpty
              ? const _EmptyState()
              : ListView(
                  padding: const EdgeInsets.fromLTRB(20, 8, 20, 100),
                  children: [
                    if (rooms.nearestRoom case final nearest?)
                      FeaturedRoomCard(
                        room: nearest,
                        onTap: () => context.go('/rooms/${nearest.id}'),
                      ),
                    if (rooms.otherRooms.isNotEmpty) ...[
                      const SizedBox(height: 24),
                      Text(
                        'All rooms',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 8),
                      for (final room in rooms.otherRooms)
                        CompactRoomCard(
                          room: room,
                          onTap: () => context.go('/rooms/${room.id}'),
                        ),
                    ],
                  ],
                ),
        ),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(24),
    children: [
      const SizedBox(height: 96),
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
      const Text(
        'Create something worth looking forward to.',
        textAlign: TextAlign.center,
      ),
    ],
  );
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(24),
    children: [
      const SizedBox(height: 96),
      const Icon(Icons.cloud_off, size: 64),
      const SizedBox(height: 16),
      Text(message, textAlign: TextAlign.center),
      const SizedBox(height: 16),
      FilledButton(onPressed: onRetry, child: const Text('Retry')),
    ],
  );
}
