import 'package:flutter/material.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_app_bar.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/room_widget_providers.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_formatters.dart';

class RoomWidgetSettingsScreen extends ConsumerWidget {
  const RoomWidgetSettingsScreen({this.appWidgetId, super.key});
  final int? appWidgetId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rooms = ref.watch(roomListControllerProvider);
    final provider = roomWidgetControllerProvider(appWidgetId);
    final widget = ref.watch(provider);
    return Scaffold(
      appBar: const RoomAppBar(title: 'Countdown widgets'),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async {
            await widget.load();
            await rooms.load();
          },
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(24),
            children: [
              Text(
                'Your next moment, at a glance.',
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 12),
              const Text(
                'Add a Hyped widget from your Android home screen, then choose a room for each widget here.',
              ),
              const SizedBox(height: 12),
              const Text(
                'Countdown text changes when the widget updates. Widgets keep the last successful sync while offline and are not continuously network-synchronized.',
              ),
              const SizedBox(height: 24),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(
                            Icons.android,
                            color: Theme.of(context).colorScheme.primary,
                          ),
                          const SizedBox(width: 8),
                          Text(
                            'Android home screen',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      Row(
                        children: [
                          Expanded(
                            child: _WidgetSizePreview(
                              label: 'Small',
                              icon: Icons.crop_square,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: _WidgetSizePreview(
                              label: 'Medium',
                              icon: Icons.crop_landscape,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      const Text(
                        '1. Touch and hold an empty area on your home screen.\n2. Open Widgets and choose Hyped.\n3. Add a Small or Medium widget, then choose its room below.',
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 20),
              if (widget.loading || rooms.loading)
                const LinearProgressIndicator(),
              if (widget.message case final message?) Text(message),
              if (!widget.loading && widget.instances.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: Text('No home-screen widgets installed.'),
                ),
              if (widget.instances.isNotEmpty) ...[
                const SizedBox(height: 20),
                DropdownButtonFormField<int>(
                  key: ValueKey(widget.selectedId),
                  initialValue: widget.selectedId,
                  decoration: const InputDecoration(
                    labelText: 'Home-screen widget',
                    border: OutlineInputBorder(),
                  ),
                  items: [
                    for (final instance in widget.instances)
                      DropdownMenuItem(
                        value: instance.id,
                        child: Text(
                          '${instance.size == 'medium' ? 'Medium' : 'Small'} widget ${instance.id}',
                        ),
                      ),
                  ],
                  onChanged: widget.loading
                      ? null
                      : (id) {
                          if (id != null) widget.chooseWidget(id);
                        },
                ),
                const SizedBox(height: 12),
                if (rooms.error case final error?) Text(error),
                if (!rooms.loading && rooms.rooms.isEmpty)
                  const Text(
                    'Create or join a room to select it for this widget.',
                  ),
                for (final room in rooms.rooms)
                  Card(
                    child: ListTile(
                      leading: Icon(
                        widget.selectedRoomId == room.id
                            ? Icons.radio_button_checked
                            : Icons.radio_button_unchecked,
                      ),
                      title: Text(room.title),
                      subtitle: Text(compactDateTime(room.eventAt)),
                      onTap: widget.loading ? null : () => widget.select(room),
                    ),
                  ),
                OutlinedButton(
                  onPressed: widget.loading ? null : widget.clear,
                  child: const Text('Clear this widget'),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _WidgetSizePreview extends StatelessWidget {
  const _WidgetSizePreview({required this.label, required this.icon});
  final String label;
  final IconData icon;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.surfaceContainerLow,
      borderRadius: BorderRadius.circular(16),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: Theme.of(context).colorScheme.primary, size: 32),
        const SizedBox(height: 12),
        Text(label, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 4),
        Text('Choose a room', style: Theme.of(context).textTheme.bodySmall),
      ],
    ),
  );
}
