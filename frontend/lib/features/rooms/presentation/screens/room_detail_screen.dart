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
      appBar: AppBar(
        title: const Text('Room'),
        actions: [
          if (room?.canEdit == true)
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
            : _RoomDetail(room: room, onRefresh: controller.load),
      ),
    );
  }
}

class _RoomDetail extends StatelessWidget {
  const _RoomDetail({required this.room, required this.onRefresh});

  final Room room;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
        children: [
          RoomThemeView(
            theme: room.theme,
            padding: const EdgeInsets.all(24),
            child: DefaultTextStyle(
              style: Theme.of(context).textTheme.bodyLarge!.copyWith(
                color: Colors.white,
                shadows: const [Shadow(color: Colors.black26, blurRadius: 8)],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    room.title,
                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                      color: Colors.white,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 28),
                  Text(
                    countdown(room.eventAt, DateTime.now()),
                    style: Theme.of(context).textTheme.displayMedium?.copyWith(
                      color: Colors.white,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(compactDateTime(room.eventAt)),
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
  });

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) => Card(
    child: ListTile(
      leading: Icon(icon),
      title: Text(title),
      subtitle: Text(subtitle),
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
