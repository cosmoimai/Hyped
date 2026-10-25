import 'package:flutter/material.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_app_bar.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hyped/features/notifications/presentation/push_providers.dart';

class NotificationSettingsScreen extends ConsumerWidget {
  const NotificationSettingsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final push = ref.watch(pushControllerProvider);
    return Scaffold(
      appBar: const RoomAppBar(title: 'Notifications'),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const Text(
            'Get event reminders',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 12),
          const Text(
            'Hyped can remind you 24 hours before, 1 hour before, and when an event starts. Change each room’s reminders from its reminder settings. Notification previews never include room details.',
          ),
          const SizedBox(height: 16),
          if (!push.status.configured)
            const Text(
              'Firebase notifications are not configured for this build.',
            ),
          if (push.status.enabled)
            const Text('Notifications are enabled on this device.'),
          if (push.message case final message?) Text(message),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: push.busy || !push.status.configured
                ? null
                : push.requestPermission,
            child: const Text('Enable notifications'),
          ),
          TextButton(
            onPressed: push.busy ? null : () => push.platform.openSettings(),
            child: const Text('Open Android settings'),
          ),
          TextButton(
            onPressed: push.busy ? null : () => push.sync(force: true),
            child: const Text('Retry registration'),
          ),
        ],
      ),
    );
  }
}
