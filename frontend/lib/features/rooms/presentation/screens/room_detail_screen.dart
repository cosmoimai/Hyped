import 'package:hyped/core/presentation/countdown_mark.dart';
import 'package:hyped/features/rooms/presentation/widgets/countdown_readout.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_app_bar.dart';
import 'package:hyped/features/rooms/presentation/widgets/sync_status.dart';
import 'package:hyped/features/rooms/domain/room_lifecycle.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_formatters.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_theme_view.dart';

class RoomDetailScreen extends ConsumerWidget {
  const RoomDetailScreen({required this.roomId, super.key});

  final String roomId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.watch(roomDetailControllerProvider(roomId));
    final room = controller.room;
    return Scaffold(
      appBar: RoomAppBar(
        title: 'Room',
        actions: [
          if (room != null && !controller.readOnly)
            IconButton(
              onPressed: () => context.go('/rooms/$roomId/lifecycle'),
              tooltip: 'Room actions',
              icon: const Icon(Icons.more_horiz),
            ),
          if (room?.canEdit == true && !controller.readOnly)
            IconButton(
              onPressed: () => context.go('/rooms/$roomId/edit'),
              tooltip: 'Edit room',
              icon: const Icon(Icons.edit),
            ),
        ],
      ),
      body: SafeArea(
        child: controller.loading && room == null
            ? const Center(child: CircularProgressIndicator())
            : controller.error != null && room == null
            ? _DetailError(message: controller.error!, onRetry: controller.load)
            : room == null
            ? const Center(child: Text('Room unavailable.'))
            : Column(
                children: [
                  if (room.lastSyncedAt != null || controller.error != null)
                    SyncStatus(
                      syncedAt: room.lastSyncedAt,
                      readOnly: controller.readOnly,
                      message: controller.error,
                    ),
                  Expanded(
                    child: _RoomDetail(
                      room: room,
                      onRefresh: controller.load,
                      readOnly: controller.readOnly,
                    ),
                  ),
                ],
              ),
      ),
    );
  }
}

class _RoomDetail extends StatelessWidget {
  const _RoomDetail({
    required this.room,
    required this.onRefresh,
    required this.readOnly,
  });

  final Room room;
  final bool readOnly;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
        children: [
          if (room.status != RoomStatus.active)
            Padding(
              padding: const EdgeInsets.only(bottom: 16),
              child: Text(lifecycleLabel(room, room.serverNow)),
            ),
          RoomThemeView(
            theme: room.theme,
            scrim: true,
            padding: const EdgeInsets.all(24),
            child: DefaultTextStyle(
              style: Theme.of(context).textTheme.bodyLarge!.copyWith(
                color: Colors.white,
                shadows: const [Shadow(color: Colors.black26, blurRadius: 8)],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const SizedBox(height: 24),
                  const Center(
                    child: CountdownMark(size: 84, color: Color(0xFFB8C7FF)),
                  ),
                  const SizedBox(height: 24),
                  if (room.location case final location?) ...[
                    Text(location, textAlign: TextAlign.center),
                    const SizedBox(height: 12),
                  ],
                  Text(
                    room.title,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                      color: Colors.white,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 28),
                  CountdownReadout(eventAt: room.eventAt, onDark: true),
                  const SizedBox(height: 24),
                  Text(
                    compactDateTime(room.eventAt),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'In your device’s local time',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 12, color: Color(0xFFD5DFFF)),
                  ),
                  const SizedBox(height: 24),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),
          _InfoTile(
            icon: Icons.groups_outlined,
            title:
                '${room.memberCount} member${room.memberCount == 1 ? '' : 's'}',
            subtitle: _roleLabel(room.role),
            onTap: readOnly
                ? null
                : () => context.go('/rooms/${room.id}/members'),
          ),
          if (!readOnly && room.status == RoomStatus.active)
            _InfoTile(
              icon: Icons.notifications_outlined,
              title: 'Personal reminders',
              subtitle: '24 hours before, 1 hour before, and event time',
              onTap: () => context.go('/rooms/${room.id}/reminders'),
            ),
          _InfoTile(
            icon: Icons.public,
            title: room.eventTimeZone,
            subtitle: 'Event timezone',
          ),
          if (room.location case final location?)
            _InfoTile(
              icon: Icons.place_outlined,
              title: location,
              subtitle: 'Location',
            ),
          if (room.description case final description?) ...[
            const SizedBox(height: 12),
            Text('Description', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            Text(description),
          ],
        ],
      ),
    );
  }
}

class _InfoTile extends StatelessWidget {
  const _InfoTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => Card(
    child: ListTile(
      leading: Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Icon(icon),
      ),
      title: Text(title),
      subtitle: Text(subtitle),
      onTap: onTap,
      trailing: onTap == null ? null : const Icon(Icons.chevron_right),
    ),
  );
}

class _DetailError extends StatelessWidget {
  const _DetailError({required this.message, required this.onRetry});

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 16),
          FilledButton(onPressed: onRetry, child: const Text('Retry')),
        ],
      ),
    ),
  );
}

String _roleLabel(RoomRole role) => switch (role) {
  RoomRole.owner => 'Owner',
  RoomRole.coHost => 'Co-host',
  RoomRole.member => 'Member',
};
