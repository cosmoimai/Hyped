import 'package:flutter/material.dart';
import 'package:hyped/core/presentation/countdown_mark.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_lifecycle.dart';
import 'package:hyped/features/rooms/presentation/widgets/countdown_readout.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_formatters.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_theme_view.dart';

class FeaturedRoomCard extends StatelessWidget {
  const FeaturedRoomCard({required this.room, required this.onTap, super.key});
  final Room room;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    child: Material(
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(20),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Ink(
          child: RoomThemeView(
            theme: room.theme,
            scrim: true,
            padding: const EdgeInsets.all(24),
            child: DefaultTextStyle(
              style: Theme.of(
                context,
              ).textTheme.bodyMedium!.copyWith(color: Colors.white),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    children: [
                      const Icon(
                        Icons.auto_awesome_outlined,
                        color: Colors.white,
                        size: 18,
                      ),
                      const SizedBox(width: 8),
                      const Expanded(child: Text('Next up')),
                      const Icon(
                        Icons.north_east,
                        color: Colors.white,
                        size: 20,
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),
                  const Center(
                    child: CountdownMark(size: 64, color: Color(0xFFB8C7FF)),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    room.title,
                    textAlign: TextAlign.center,
                    style: Theme.of(
                      context,
                    ).textTheme.headlineLarge?.copyWith(color: Colors.white),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    compactDateTime(room.eventAt),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 24),
                  CountdownReadout(eventAt: room.eventAt, onDark: true),
                  const SizedBox(height: 20),
                  const Divider(color: Colors.white24, height: 1),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      const Icon(
                        Icons.people_outline,
                        color: Color(0xFFB8C7FF),
                        size: 20,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          '${room.memberCount} counting down together',
                        ),
                      ),
                      const Icon(
                        Icons.arrow_forward,
                        color: Colors.white,
                        size: 20,
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

class CompactRoomCard extends StatelessWidget {
  const CompactRoomCard({required this.room, required this.onTap, super.key});
  final Room room;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Card(
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    child: ListTile(
      onTap: onTap,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      leading: Container(
        width: 52,
        height: 52,
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(16),
        ),
        child: const Center(child: CountdownMark(size: 36)),
      ),
      title: Text(
        room.title,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: Theme.of(
          context,
        ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
      ),
      subtitle: Padding(
        padding: const EdgeInsets.only(top: 4),
        child: Text(
          room.status == RoomStatus.active
              ? '${compactDate(room.eventAt)} • ${countdown(room.eventAt, DateTime.now())}'
              : lifecycleLabel(room, room.serverNow),
        ),
      ),
      trailing: const Icon(Icons.chevron_right, size: 20),
    ),
  );
}
