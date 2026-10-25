import 'package:hyped/features/rooms/domain/room.dart';

enum RoomLifecycleAction { leave, archive, restore, delete }

List<RoomLifecycleAction> lifecycleActions(Room room, DateTime now) {
  if (room.status == RoomStatus.deleting) return const [];
  if (room.role != RoomRole.owner) return const [RoomLifecycleAction.leave];
  if (room.status == RoomStatus.active) {
    return const [RoomLifecycleAction.archive];
  }
  final deadline = room.deleteAfter;
  if (deadline == null || room.moderationHold && now.isBefore(deadline)) {
    return const [];
  }
  return [
    now.isBefore(deadline)
        ? RoomLifecycleAction.restore
        : RoomLifecycleAction.delete,
  ];
}

String lifecycleLabel(Room room, DateTime now) => switch (room.status) {
  RoomStatus.active => 'Active',
  RoomStatus.deleting => 'Room removed',
  RoomStatus.archived
      when room.moderationHold &&
          room.deleteAfter != null &&
          now.isBefore(room.deleteAfter!) =>
    'Archived · under review',
  RoomStatus.archived =>
    room.deleteAfter != null && !now.isBefore(room.deleteAfter!)
        ? 'Archived · eligible for permanent deletion'
        : 'Archived · within the 24-hour recovery period',
};

String lifecycleActionLabel(RoomLifecycleAction action) => switch (action) {
  RoomLifecycleAction.leave => 'Leave room',
  RoomLifecycleAction.archive => 'Archive room',
  RoomLifecycleAction.restore => 'Restore room',
  RoomLifecycleAction.delete => 'Permanently delete room',
};
