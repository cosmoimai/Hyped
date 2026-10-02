import 'package:flutter/material.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_formatters.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_theme_view.dart';

class FeaturedRoomCard extends StatelessWidget {
  const FeaturedRoomCard({required this.room, required this.onTap, super.key});

  final Room room;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(28),
      onTap: onTap,
      child: RoomThemeView(
        theme: room.theme,
        child: DefaultTextStyle(
          style: Theme.of(context).textTheme.bodyMedium!.copyWith(
            color: Colors.white,
            shadows: const [Shadow(color: Colors.black26, blurRadius: 8)],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Next up'),
              const SizedBox(height: 36),
              Text(
                room.title,
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  color: Colors.white,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 8),
              Text(compactDateTime(room.eventAt)),
              const SizedBox(height: 20),
              Text(
                countdown(room.eventAt, DateTime.now()),
                style: Theme.of(context).textTheme.displaySmall?.copyWith(
                  color: Colors.white,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                '${room.memberCount} member${room.memberCount == 1 ? '' : 's'}',
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class CompactRoomCard extends StatelessWidget {
  const CompactRoomCard({required this.room, required this.onTap, super.key});

  final Room room;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        onTap: onTap,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 10,
        ),
        leading: ClipRRect(
          borderRadius: BorderRadius.circular(14),
          child: SizedBox(
            width: 52,
            height: 52,
            child: RoomThemeView(
              theme: room.theme,
              padding: EdgeInsets.zero,
              child: const SizedBox.expand(),
            ),
          ),
        ),
        title: Text(room.title, maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Text(
          '${compactDate(room.eventAt)} • ${countdown(room.eventAt, DateTime.now())}',
        ),
        trailing: const Icon(Icons.chevron_right),
      ),
    );
  }
}
