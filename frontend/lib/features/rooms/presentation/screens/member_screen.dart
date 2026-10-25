import 'package:hyped/features/rooms/presentation/widgets/room_app_bar.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/room_widget_providers.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/rooms/presentation/controllers/member_controller.dart';
import 'package:hyped/features/rooms/data/member_api.dart';
import 'package:hyped/features/rooms/domain/room_member.dart';

final memberApiProvider = Provider<MemberApi>(
  (ref) => MemberApi(ref.read(authenticatedDioProvider)),
);
final memberControllerProvider = ChangeNotifierProvider.autoDispose
    .family<MemberController, String>((ref, id) {
      ref.watch(authControllerProvider.select((auth) => auth.state.status));
      final controller = MemberController(
        ref.read(memberApiProvider),
        id,
        ref.read(roomWidgetRepositoryProvider),
      );
      controller.load();
      return controller;
    });

class MemberScreen extends ConsumerWidget {
  const MemberScreen({required this.roomId, super.key});
  final String roomId;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.watch(memberControllerProvider(roomId));
    final snapshot = controller.snapshot;
    return Scaffold(
      appBar: RoomAppBar(
        title: 'Members · ${snapshot?.items.length ?? '—'}/25',
        backLocation: '/rooms/$roomId',
      ),
      body: Column(
        children: [
          if (controller.loading || controller.saving)
            const LinearProgressIndicator(),
          if (controller.error case final error?)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Text(error),
                  TextButton(
                    onPressed: controller.loading || controller.saving
                        ? null
                        : controller.load,
                    child: const Text('Reload members'),
                  ),
                ],
              ),
            ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: controller.load,
              child: ListView(
                children: [
                  if (snapshot != null)
                    for (final member in snapshot.items)
                      ListTile(
                        leading: CircleAvatar(
                          child: Text(
                            member.displayName.trim().isEmpty
                                ? '?'
                                : member.displayName
                                      .trim()
                                      .characters
                                      .first
                                      .toUpperCase(),
                          ),
                        ),
                        title: Text(member.displayName),
                        subtitle: Text(
                          '${_role(member.role)}${member.isCurrentUser ? ' · You' : ''}',
                        ),
                        trailing:
                            memberActions(snapshot, member).isEmpty ||
                                controller.stale
                            ? null
                            : PopupMenuButton<MemberAction>(
                                enabled:
                                    !controller.saving && !controller.loading,
                                tooltip: 'Manage ${member.displayName}',
                                onSelected: (action) async {
                                  final confirmed = await showDialog<bool>(
                                    context: context,
                                    builder: (context) => AlertDialog(
                                      title: Text('${_label(action)}?'),
                                      content: Text(
                                        _explanation(
                                          action,
                                          member.displayName,
                                        ),
                                      ),
                                      actions: [
                                        TextButton(
                                          onPressed: () =>
                                              Navigator.pop(context, false),
                                          child: const Text('Cancel'),
                                        ),
                                        FilledButton(
                                          onPressed: () =>
                                              Navigator.pop(context, true),
                                          child: Text(_label(action)),
                                        ),
                                      ],
                                    ),
                                  );
                                  if (confirmed != true || !context.mounted) {
                                    return;
                                  }
                                  if (await controller.change(member, action) &&
                                      context.mounted) {
                                    ref.invalidate(
                                      roomDetailControllerProvider(roomId),
                                    );
                                    ref.invalidate(roomListControllerProvider);
                                  }
                                },
                                itemBuilder: (_) => [
                                  for (final action in memberActions(
                                    snapshot,
                                    member,
                                  ))
                                    PopupMenuItem(
                                      value: action,
                                      child: Text(_label(action)),
                                    ),
                                ],
                              ),
                      ),
                  if (snapshot == null &&
                      !controller.loading &&
                      controller.error == null)
                    const ListTile(
                      title: Text('No member data. Pull to reload.'),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

String _role(RoomRole role) => switch (role) {
  RoomRole.owner => 'Owner',
  RoomRole.coHost => 'Co-host',
  RoomRole.member => 'Member',
};
String _label(MemberAction action) => switch (action) {
  MemberAction.promote => 'Make co-host',
  MemberAction.demote => 'Make member',
  MemberAction.remove => 'Remove member',
  MemberAction.transfer => 'Transfer ownership',
};
String _explanation(MemberAction action, String name) => switch (action) {
  MemberAction.promote =>
    '$name will be able to edit the room and remove regular members.',
  MemberAction.demote => '$name will lose co-host management permissions.',
  MemberAction.remove =>
    '$name will lose access and reminders. They can rejoin with a valid invitation. Regenerate the invitation if you need to prevent re-entry.',
  MemberAction.transfer =>
    '$name will become the owner. You will become a co-host and lose owner-only permissions.',
};
