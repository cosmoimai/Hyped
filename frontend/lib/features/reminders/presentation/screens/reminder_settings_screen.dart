import 'package:hyped/features/rooms/presentation/widgets/room_app_bar.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hyped/features/reminders/domain/reminder_preferences.dart';
import 'package:hyped/features/reminders/presentation/controllers/reminder_providers.dart';

class ReminderSettingsScreen extends ConsumerWidget {
  const ReminderSettingsScreen({required this.roomId, super.key});

  final String roomId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.watch(reminderControllerProvider(roomId));
    final preferences = controller.preferences;
    return Scaffold(
      appBar: RoomAppBar(title: 'Reminders', backLocation: '/rooms/$roomId'),
      body: SafeArea(
        child: controller.loading && preferences == null
            ? const Center(child: CircularProgressIndicator())
            : preferences == null
            ? _Error(
                message: controller.error ?? 'Reminders unavailable',
                onRetry: controller.load,
              )
            : ListView(
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
                children: [
                  const Text('Choose which reminders you want for this room.'),
                  const SizedBox(height: 12),
                  if (controller.error case final error?)
                    Card(
                      color: Theme.of(context).colorScheme.errorContainer,
                      child: ListTile(title: Text(error)),
                    ),
                  _ReminderTile(
                    title: '24 hours before',
                    value: preferences.remind24HoursBefore,
                    enabled: !controller.saving,
                    onChanged: (value) =>
                        _save(ref, preferences, remind24HoursBefore: value),
                  ),
                  _ReminderTile(
                    title: '1 hour before',
                    value: preferences.remind1HourBefore,
                    enabled: !controller.saving,
                    onChanged: (value) =>
                        _save(ref, preferences, remind1HourBefore: value),
                  ),
                  _ReminderTile(
                    title: 'At event time',
                    value: preferences.remindAtEventTime,
                    enabled: !controller.saving,
                    onChanged: (value) =>
                        _save(ref, preferences, remindAtEventTime: value),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Notifications may require permission in Android settings.',
                  ),
                ],
              ),
      ),
    );
  }

  void _save(
    WidgetRef ref,
    ReminderPreferences current, {
    bool? remind24HoursBefore,
    bool? remind1HourBefore,
    bool? remindAtEventTime,
  }) {
    ref
        .read(reminderControllerProvider(roomId))
        .replace(
          ReminderPreferences(
            remind24HoursBefore:
                remind24HoursBefore ?? current.remind24HoursBefore,
            remind1HourBefore: remind1HourBefore ?? current.remind1HourBefore,
            remindAtEventTime: remindAtEventTime ?? current.remindAtEventTime,
            updatedAt: current.updatedAt,
          ),
        );
  }
}

class _ReminderTile extends StatelessWidget {
  const _ReminderTile({
    required this.title,
    required this.value,
    required this.enabled,
    required this.onChanged,
  });

  final String title;
  final bool value;
  final bool enabled;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) => SwitchListTile(
    title: Text(title),
    value: value,
    onChanged: enabled ? onChanged : null,
  );
}

class _Error extends StatelessWidget {
  const _Error({required this.message, required this.onRetry});

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(message),
        const SizedBox(height: 12),
        FilledButton(onPressed: onRetry, child: const Text('Retry')),
      ],
    ),
  );
}
