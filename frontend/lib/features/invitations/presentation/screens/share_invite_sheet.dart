import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/room_widget_providers.dart';
import 'package:hyped/features/invitations/data/invitation_share_platform.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_providers.dart';
import 'package:hyped/features/invitations/presentation/controllers/share_invitation_controller.dart';

final invitationSharePlatformProvider = Provider<InvitationSharePlatform>(
  (ref) => const InvitationSharePlatform(),
);
final shareInvitationControllerProvider = ChangeNotifierProvider.autoDispose
    .family<ShareInvitationController, String>((ref, id) {
      final controller = ShareInvitationController(
        ref.read(invitationApiProvider),
        ref.read(tokenStoreProvider),
        ref.read(invitationSharePlatformProvider),
        ref.read(roomWidgetRepositoryProvider),
        id,
      );
      ref.listen(
        authControllerProvider.select((auth) => auth.state.status),
        (_, _) => controller.invalidate(),
      );
      controller.load();
      return controller;
    });

Future<void> showShareInvite(BuildContext context, String roomId) =>
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => ShareInviteSheet(roomId: roomId),
    );

class ShareInviteSheet extends ConsumerWidget {
  const ShareInviteSheet({required this.roomId, super.key});
  final String roomId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.watch(shareInvitationControllerProvider(roomId));
    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'Invite your people',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 12),
            const Text(
              'Anyone with this code can preview and join your room. Share it only with people you want to invite.',
            ),
            const SizedBox(height: 24),
            if (controller.busy)
              const Center(child: CircularProgressIndicator()),
            if (controller.invitation case final invitation?)
              Container(
                padding: const EdgeInsets.all(24),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.surfaceContainerLow,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Column(
                  children: [
                    const Text('ROOM CODE'),
                    const SizedBox(height: 8),
                    Text(
                      invitation.roomCode,
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Open Hyped → Join → Enter room code',
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
            if (controller.error case final error?) ...[
              Text(error),
              TextButton(
                onPressed: controller.busy ? null : controller.load,
                child: const Text('Retry'),
              ),
            ],
            const SizedBox(height: 24),
            FilledButton.icon(
              onPressed: controller.busy || controller.invitation == null
                  ? null
                  : controller.share,
              icon: const Icon(Icons.ios_share),
              label: const Text('Share invite'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Close'),
            ),
          ],
        ),
      ),
    );
  }
}
