import 'package:hyped/features/rooms/presentation/screens/room_lifecycle_screen.dart';
import 'package:hyped/features/invitations/presentation/screens/share_invite_sheet.dart';
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
        title: 'Event detail',
        actions: [
          if (room != null && !controller.readOnly)
            IconButton(
              onPressed: () => showModalBottomSheet<void>(
                context: context,
                isScrollControlled: true,
                showDragHandle: true,
                builder: (_) => SizedBox(
                  height: MediaQuery.sizeOf(context).height * .82,
                  child: RoomLifecycleScreen(roomId: roomId, asSheet: true),
                ),
              ),
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
      bottomNavigationBar:
          room?.status == RoomStatus.active && !controller.readOnly
          ? SafeArea(
              top: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
                child: Wrap(
                  alignment: WrapAlignment.center,
                  spacing: 12,
                  runSpacing: 8,
                  children: [
                    FilledButton.icon(
                      onPressed: () => showShareInvite(context, roomId),
                      icon: const Icon(Icons.ios_share),
                      label: const Text('Share invite'),
                    ),
                    FilledButton.tonalIcon(
                      onPressed: () => context.go('/rooms/$roomId/reminders'),
                      icon: const Icon(Icons.notifications_active_outlined),
                      label: const Text('Set reminder'),
                    ),
                  ],
                ),
              ),
            )
          : null,
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
    return LayoutBuilder(
      builder: (context, constraints) => RefreshIndicator(
        onRefresh: onRefresh,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          child: RoomThemeView(
            theme: room.theme,
            scrim: true,
            borderRadius: 0,
            padding: const EdgeInsets.fromLTRB(24, 32, 24, 32),
            child: ConstrainedBox(
              constraints: BoxConstraints(
                minHeight: (constraints.maxHeight - 64).clamp(
                  0,
                  double.infinity,
                ),
              ),
              child: DefaultTextStyle(
                style: Theme.of(
                  context,
                ).textTheme.bodyLarge!.copyWith(color: Colors.white),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    if (room.status != RoomStatus.active)
                      Text(
                        lifecycleLabel(room, room.serverNow),
                        textAlign: TextAlign.center,
                      ),
                    SizedBox(height: constraints.maxHeight > 600 ? 64 : 24),
                    if (room.location case final location?)
                      Align(
                        alignment: Alignment.center,
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 10,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: .16),
                            borderRadius: BorderRadius.circular(40),
                          ),
                          child: Text(location, textAlign: TextAlign.center),
                        ),
                      ),
                    const SizedBox(height: 20),
                    Text(
                      room.title,
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.displaySmall?.copyWith(
                        color: Colors.white,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 32),
                    CountdownReadout(eventAt: room.eventAt, onDark: true),
                    const SizedBox(height: 28),
                    Text(
                      compactDateTime(room.eventAt),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'In your device’s local time',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 13, color: Color(0xFFD5DFFF)),
                    ),
                    const SizedBox(height: 24),
                    Material(
                      color: Colors.white.withValues(alpha: .16),
                      borderRadius: BorderRadius.circular(40),
                      child: ListTile(
                        leading: const Icon(
                          Icons.people_outline,
                          color: Color(0xFFB8C7FF),
                        ),
                        title: Text(
                          '${room.memberCount} member${room.memberCount == 1 ? '' : 's'}',
                          style: const TextStyle(color: Colors.white),
                        ),
                        subtitle: Text(
                          '${_roleLabel(room.role)} · Counting down together',
                          style: const TextStyle(color: Color(0xFFD5DFFF)),
                        ),
                        trailing: readOnly
                            ? null
                            : const Icon(
                                Icons.chevron_right,
                                color: Colors.white,
                              ),
                        onTap: readOnly
                            ? null
                            : () => context.go('/rooms/${room.id}/members'),
                      ),
                    ),
                    const SizedBox(height: 24),
                    Card(
                      child: ExpansionTile(
                        title: const Text('About this room'),
                        leading: const Icon(Icons.info_outline),
                        children: [
                          _InfoTile(
                            icon: Icons.public,
                            title: room.eventTimeZone,
                            subtitle: 'Event timezone',
                          ),
                          if (room.description case final description?)
                            Padding(
                              padding: const EdgeInsets.all(16),
                              child: Text(description),
                            ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 24),
                  ],
                ),
              ),
            ),
          ),
        ),
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
