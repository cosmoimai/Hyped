import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_app_bar.dart';
import 'package:hyped/features/safety/presentation/safety_providers.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/media/presentation/room_photo_providers.dart';

class BlockedAccountsScreen extends ConsumerWidget {
  const BlockedAccountsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final accounts = ref.watch(blockedAccountsProvider);
    final controller = ref.watch(safetyControllerProvider);
    return Scaffold(
      appBar: const RoomAppBar(
        title: 'Blocked accounts',
        backLocation: '/settings',
      ),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const Text(
            'Only accounts you blocked appear here. Unblocking does not restore room membership or roles.',
          ),
          if (controller.busy) const LinearProgressIndicator(),
          if (controller.error case final error?) Text(error),
          accounts.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (error, _) => Column(
              children: [
                Text(safetyError(error)),
                TextButton(
                  onPressed: () => ref.invalidate(blockedAccountsProvider),
                  child: const Text('Reload'),
                ),
              ],
            ),
            data: (items) => Column(
              children: [
                if (items.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(24),
                    child: Text('No blocked accounts.'),
                  ),
                for (final account in items)
                  ListTile(
                    title: Text(account.displayName),
                    trailing: TextButton(
                      onPressed: controller.busy
                          ? null
                          : () async {
                              final confirmed = await showDialog<bool>(
                                context: context,
                                builder: (_) => AlertDialog(
                                  title: Text(
                                    'Unblock ${account.displayName}?',
                                  ),
                                  content: const Text(
                                    'This allows future room interactions. Previous memberships and roles will not be restored.',
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
                                      child: const Text('Unblock'),
                                    ),
                                  ],
                                ),
                              );
                              if (confirmed == true &&
                                  await controller.unblock(account.userId) &&
                                  context.mounted) {
                                ref.invalidate(blockedAccountsProvider);
                                ref.invalidate(roomListControllerProvider);
                                ref.invalidate(roomDetailControllerProvider);
                                ref.invalidate(roomPhotoBytesProvider);
                              }
                            },
                      child: const Text('Unblock'),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
