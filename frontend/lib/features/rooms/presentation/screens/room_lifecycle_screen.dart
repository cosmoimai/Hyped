import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/room_widget_providers.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_lifecycle.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_lifecycle_controller.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/rooms/presentation/screens/member_screen.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_formatters.dart';

final roomLifecycleControllerProvider = ChangeNotifierProvider.autoDispose
    .family<RoomLifecycleController, String>((ref, id) {
      ref.watch(authControllerProvider.select((auth) => auth.state.status));
      final controller = RoomLifecycleController(
        ref.read(roomRepositoryProvider),
        id,
        ref.read(roomWidgetRepositoryProvider),
      );
      controller.load();
      return controller;
    });

class RoomLifecycleScreen extends ConsumerWidget {
  const RoomLifecycleScreen({required this.roomId, super.key});
  final String roomId;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.watch(roomLifecycleControllerProvider(roomId));
    final room = controller.room;
    return Scaffold(
      appBar: AppBar(title: const Text('Room actions')),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          if (controller.loading || controller.saving)
            const LinearProgressIndicator(),
          if (room != null) ...[
            Text(room.title, style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: 12),
            Text(lifecycleLabel(room, controller.now)),
            if (room.deleteAfter case final deadline?)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  'Recovery ends and permanent deletion becomes eligible at ${compactDateTime(deadline)}.',
                ),
              ),
            if (room.role == RoomRole.owner) ...[
              const SizedBox(height: 12),
              Text(
                room.status == RoomStatus.archived
                    ? 'Restore this room before transferring ownership. Owners must transfer before leaving.'
                    : 'Transfer ownership from Members before leaving this room.',
              ),
              TextButton(
                onPressed: controller.saving
                    ? null
                    : () => context.go('/rooms/$roomId/members'),
                child: const Text('Open Members'),
              ),
            ],
          ],
          if (controller.error case final error?) ...[
            const SizedBox(height: 16),
            Text(error),
            TextButton(
              onPressed: controller.loading || controller.saving
                  ? null
                  : controller.load,
              child: const Text('Reload room'),
            ),
          ],
          if (controller.message case final message?)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: Text(message),
            ),
          for (final action in controller.actions)
            Padding(
              padding: const EdgeInsets.only(top: 16),
              child: OutlinedButton(
                onPressed: controller.loading || controller.saving
                    ? null
                    : () async {
                        final confirmed = await showDialog<bool>(
                          context: context,
                          builder: (context) => AlertDialog(
                            title: Text('${lifecycleActionLabel(action)}?'),
                            content: Text(_impact(action)),
                            actions: [
                              TextButton(
                                onPressed: () => Navigator.pop(context, false),
                                child: const Text('Cancel'),
                              ),
                              FilledButton(
                                onPressed: () => Navigator.pop(context, true),
                                child: Text(lifecycleActionLabel(action)),
                              ),
                            ],
                          ),
                        );
                        if (confirmed != true || !context.mounted) return;
                        if (await controller.change(action) &&
                            context.mounted) {
                          ref.invalidate(roomDetailControllerProvider(roomId));
                          ref.invalidate(roomListControllerProvider);
                          ref.invalidate(memberControllerProvider(roomId));
                        }
                      },
                child: Text(lifecycleActionLabel(action)),
              ),
            ),
          if (controller.removed)
            FilledButton(
              onPressed: () => context.go('/home'),
              child: const Text('Back to rooms'),
            ),
        ],
      ),
    );
  }
}

String _impact(RoomLifecycleAction action) => switch (action) {
  RoomLifecycleAction.leave =>
    'You will lose access and reminders. Widgets selecting this room will stop showing it. You may rejoin with a valid invitation.',
  RoomLifecycleAction.archive =>
    'Joins and active reminders will stop. Members can still view the room. Only the owner can restore it during the next 24 hours; afterward it is eligible for permanent deletion.',
  RoomLifecycleAction.restore =>
    'This room will become active again. Future fixed reminders resume without repeating already delivered reminders. Active-room limits still apply.',
  RoomLifecycleAction.delete =>
    'This permanently removes the room, invitations, memberships and reminders for everyone. Widgets lose their selection when synchronized. This cannot be undone.',
};
