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
                        Icons.place_outlined,
                        color: Colors.white,
                        size: 18,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          room.location ?? 'Next up',
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const Icon(
                        Icons.north_east,
                        color: Colors.white,
                        size: 20,
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),
                  Center(
                    child: SizedBox.square(
                      dimension: 88,
                      child: Stack(
                        alignment: Alignment.center,
                        children: [
                          SizedBox.expand(
                            child: CircularProgressIndicator(
                              value: room.eventAt.isAfter(room.createdAt)
                                  ? (DateTime.now()
                                                .difference(room.createdAt)
                                                .inSeconds /
                                            room.eventAt
                                                .difference(room.createdAt)
                                                .inSeconds)
                                        .clamp(0.0, 1.0)
                                  : 1,
                              strokeWidth: 4,
                              backgroundColor: Colors.white24,
                              color: const Color(0xFF6C8CFF),
                              semanticsLabel: 'Time since room creation',
                            ),
                          ),
                          const Icon(
                            Icons.auto_awesome_outlined,
                            color: Colors.white,
                            size: 32,
                          ),
                        ],
                      ),
                    ),
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
              ? compactDate(room.eventAt)
              : lifecycleLabel(room, room.serverNow),
        ),
      ),
      trailing: room.status == RoomStatus.active
          ? SizedBox(
              width: 72,
              child: FittedBox(
                fit: BoxFit.scaleDown,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    FittedBox(
                      fit: BoxFit.scaleDown,
                      child: Text(
                        room.eventAt.isAfter(DateTime.now())
                            ? '${room.eventAt.difference(DateTime.now()).inDays > 0 ? room.eventAt.difference(DateTime.now()).inDays : room.eventAt.difference(DateTime.now()).inHours}'
                            : '0',
                        style: Theme.of(context).textTheme.headlineSmall
                            ?.copyWith(
                              color: Theme.of(context).colorScheme.primary,
                            ),
                      ),
                    ),
                    Text(
                      room.eventAt.difference(DateTime.now()).inDays > 0
                          ? 'DAYS LEFT'
                          : 'HOURS LEFT',
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.labelSmall,
                    ),
                  ],
                ),
              ),
            )
          : const Icon(Icons.chevron_right, size: 20),
    ),
  );
}
