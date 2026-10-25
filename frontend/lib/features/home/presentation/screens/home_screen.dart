import 'package:hyped/core/presentation/countdown_mark.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/core/offline/offline_state.dart';
import 'package:hyped/features/rooms/presentation/widgets/sync_status.dart';
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
        title: const Row(
          children: [
            CountdownMark(size: 32),
            SizedBox(width: 8),
            Flexible(child: Text('Home', overflow: TextOverflow.ellipsis)),
          ],
        ),
        actions: [
          TextButton.icon(
            onPressed: () => context.go('/join'),
            icon: const Icon(Icons.key_outlined, size: 18),
            label: const Text('Join'),
          ),
          PopupMenuButton<String>(
            tooltip: 'More options',
            onSelected: (value) {
              if (value == 'logout') {
                ref.read(authControllerProvider).logout();
              } else {
                context.go(value);
              }
            },
            itemBuilder: (_) => [
              if (push.status.supported)
                const PopupMenuItem(
                  value: '/notifications',
                  child: ListTile(
                    leading: Icon(Icons.notifications_outlined),
                    title: Text('Notifications'),
                  ),
                ),
              const PopupMenuItem(
                value: '/widgets/room',
                child: ListTile(
                  leading: Icon(Icons.widgets_outlined),
                  title: Text('Countdown widgets'),
                ),
              ),
              const PopupMenuItem(
                value: 'logout',
                child: ListTile(
                  leading: Icon(Icons.logout),
                  title: Text('Sign out'),
                ),
              ),
            ],
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        top: false,
        child: Container(
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surface,
            border: Border(
              top: BorderSide(
                color: Theme.of(
                  context,
                ).colorScheme.primary.withValues(alpha: .08),
              ),
            ),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 10),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              const Expanded(
                child: _DockItem(
                  icon: Icons.home_outlined,
                  label: 'Home',
                  selected: true,
                ),
              ),
              FloatingActionButton(
                tooltip: 'Create room',
                onPressed: () {
                  if (rooms.usingCache) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text(offlineReadOnlyMessage)),
                    );
                  } else {
                    context.go('/rooms/new');
                  }
                },
                child: const Icon(Icons.add, size: 30),
              ),
              Expanded(
                child: _DockItem(
                  icon: Icons.person_outline,
                  label: 'Profile',
                  onTap: () => context.go('/settings'),
                ),
              ),
            ],
          ),
        ),
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: rooms.load,
          child: rooms.rooms.isEmpty && rooms.lastSync == null && rooms.loading
              ? const Center(child: CircularProgressIndicator())
              : rooms.rooms.isEmpty &&
                    rooms.lastSync == null &&
                    rooms.error != null
              ? _ErrorState(message: rooms.error!, onRetry: rooms.load)
              : rooms.rooms.isEmpty && rooms.lastSync == null
              ? const _EmptyState()
              : ListView(
                  padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
                  children: [
                    if (rooms.lastSync != null || rooms.error != null)
                      SyncStatus(
                        syncedAt: rooms.lastSync,
                        readOnly: rooms.usingCache,
                        message: rooms.error,
                      ),
                    if (rooms.loading) const LinearProgressIndicator(),
                    if (rooms.rooms.isEmpty)
                      const Padding(
                        padding: EdgeInsets.all(24),
                        child: Text('Your countdowns will live here'),
                      ),
                    if (push.status.supported && !push.status.enabled)
                      Card(
                        child: ListTile(
                          leading: const Icon(Icons.notifications_outlined),
                          title: const Text('Enable event reminders'),
                          subtitle: const Text(
                            'Choose whether Hyped can send notifications.',
                          ),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () => context.go('/notifications'),
                        ),
                      ),
                    if (rooms.nearestRoom != null) ...[
                      Text(
                        'Your next milestone',
                        style: Theme.of(context).textTheme.headlineSmall,
                      ),
                      const SizedBox(height: 20),
                    ],
                    if (rooms.nearestRoom case final nearest?)
                      FeaturedRoomCard(
                        room: nearest,
                        onTap: () => context.go('/rooms/${nearest.id}'),
                      ),
                    if (rooms.otherRooms.any(
                      (room) => room.status == RoomStatus.active,
                    )) ...[
                      const SizedBox(height: 24),
                      Text(
                        'More countdowns',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 8),
                      for (final room in rooms.otherRooms.where(
                        (room) => room.status == RoomStatus.active,
                      ))
                        CompactRoomCard(
                          room: room,
                          onTap: () => context.go('/rooms/${room.id}'),
                        ),
                    ],
                    if (rooms.rooms.any(
                      (room) => room.status == RoomStatus.archived,
                    )) ...[
                      const SizedBox(height: 24),
                      Card(
                        child: ExpansionTile(
                          leading: const Icon(Icons.inventory_2_outlined),
                          title: Text(
                            'Archived (${rooms.rooms.where((room) => room.status == RoomStatus.archived).length})',
                          ),
                          children: [
                            for (final room in rooms.rooms.where(
                              (room) => room.status == RoomStatus.archived,
                            ))
                              CompactRoomCard(
                                room: room,
                                onTap: () => context.go('/rooms/${room.id}'),
                              ),
                          ],
                        ),
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

class _DockItem extends StatelessWidget {
  const _DockItem({
    required this.icon,
    required this.label,
    this.selected = false,
    this.onTap,
  });
  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback? onTap;
  @override
  Widget build(BuildContext context) => Semantics(
    selected: selected,
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              color: selected ? Theme.of(context).colorScheme.primary : null,
            ),
            const SizedBox(height: 4),
            Text(label, style: Theme.of(context).textTheme.labelMedium),
          ],
        ),
      ),
    ),
  );
}
