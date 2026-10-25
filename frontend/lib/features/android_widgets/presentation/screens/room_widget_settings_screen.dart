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
              const Text(
                'Add a Hyped widget from your Android home screen, then choose a room for each widget here.',
              ),
              const SizedBox(height: 12),
              const Text(
                'Countdown text changes when the widget updates. Widgets keep the last successful sync while offline and are not continuously network-synchronized.',
              ),
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
