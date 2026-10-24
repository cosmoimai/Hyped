import 'package:flutter/material.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_formatters.dart';

class SyncStatus extends StatelessWidget {
  const SyncStatus({
    this.syncedAt,
    this.message,
    required this.readOnly,
    super.key,
  });
  final DateTime? syncedAt;
  final String? message;
  final bool readOnly;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.all(16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (readOnly)
          const Text(
            'Cached room data · Read only',
            style: TextStyle(fontWeight: FontWeight.bold),
          ),
        if (syncedAt != null) Text('Last synced ${compactDateTime(syncedAt!)}'),
        if (message != null) Text(message!),
      ],
    ),
  );
}
