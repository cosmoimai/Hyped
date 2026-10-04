import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/room_widget_providers.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_formatters.dart';

class RoomWidgetSettingsScreen extends ConsumerWidget {
  const RoomWidgetSettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rooms = ref.watch(roomListControllerProvider);
    final widget = ref.watch(roomWidgetControllerProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Countdown widget')),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async {
            await rooms.load();
            await ref.read(roomWidgetControllerProvider).load();
          },
          child: rooms.loading && rooms.rooms.isEmpty
              ? const Center(child: CircularProgressIndicator())
              : rooms.error != null && rooms.rooms.isEmpty
              ? _MessageList(message: rooms.error!, action: rooms.load)
              : rooms.rooms.isEmpty
              ? const _EmptyWidgetState()
              : ListView(
                  padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
                  children: [
                    Text(
                      'Choose a room for your Android home-screen widget.',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      'The widget uses the latest room data synced by the app and keeps showing it while offline.',
                    ),
                    if (widget.message case final message?) ...[
                      const SizedBox(height: 16),
                      Card(child: ListTile(title: Text(message))),
                    ],
                    const SizedBox(height: 16),
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
                          onTap: widget.loading
                              ? null
                              : () => ref
                                    .read(roomWidgetControllerProvider)
                                    .select(room),
                        ),
                      ),
                    const SizedBox(height: 12),
                    OutlinedButton.icon(
                      onPressed: widget.loading
                          ? null
                          : ref.read(roomWidgetControllerProvider).clear,
                      icon: const Icon(Icons.widgets_outlined),
                      label: const Text('Show empty widget state'),
                    ),
                  ],
                ),
        ),
      ),
    );
  }
}

class _EmptyWidgetState extends StatelessWidget {
  const _EmptyWidgetState();

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(24),
    children: [
      const SizedBox(height: 96),
      const Icon(Icons.widgets_outlined, size: 64),
      const SizedBox(height: 16),
      Text(
        'No rooms to show yet',
        style: Theme.of(context).textTheme.titleLarge,
        textAlign: TextAlign.center,
      ),
      const SizedBox(height: 8),
      const Text(
        'Create or join a room, then choose it here for your widget.',
        textAlign: TextAlign.center,
      ),
    ],
  );
}

class _MessageList extends StatelessWidget {
  const _MessageList({required this.message, required this.action});

  final String message;
  final Future<void> Function() action;

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(24),
    children: [
      const SizedBox(height: 96),
      Text(message, textAlign: TextAlign.center),
      const SizedBox(height: 16),
      FilledButton(onPressed: action, child: const Text('Retry')),
    ],
  );
}
