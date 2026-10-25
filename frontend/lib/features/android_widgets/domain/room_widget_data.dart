import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_formatters.dart';

/// Deliberately separate from API DTOs. This is the entire native cache allowlist.
class RoomWidgetData {
  const RoomWidgetData({
    required this.roomId,
    required this.title,
    required this.eventAt,
    required this.presetKey,
    required this.syncedAt,
    required this.state,
  });

  final String roomId;
  final String title;
  final DateTime eventAt;
  final String presetKey;
  final DateTime syncedAt;
  final String state;

  static const safePresets = {
    'soft-blue-01',
    'mint-sky-01',
    'blue-lilac-02',
    'sunset-coral-01',
    'peach-gold-01',
    'aurora-green-01',
  };

  factory RoomWidgetData.fromRoom(Room room, {DateTime? syncedAt}) =>
      RoomWidgetData(
        roomId: room.id,
        title: room.status == RoomStatus.deleting ? '' : room.title,
        eventAt: room.status == RoomStatus.deleting
            ? DateTime.fromMillisecondsSinceEpoch(0, isUtc: true)
            : room.eventAt,
        presetKey: safePresets.contains(room.theme.presetKey)
            ? room.theme.presetKey
            : 'soft-blue-01',
        syncedAt: syncedAt ?? room.lastSyncedAt ?? room.serverNow,
        state: switch (room.status) {
          RoomStatus.active => 'active',
          RoomStatus.archived => 'archived',
          RoomStatus.deleting => 'removed',
        },
      );

  Map<String, Object?> toJson() => {
    'roomId': roomId,
    'title': title,
    'eventAt': eventAt.millisecondsSinceEpoch,
    'presetKey': presetKey,
    'syncedAt': syncedAt.millisecondsSinceEpoch,
    'state': state,
  };
}

class WidgetInstance {
  const WidgetInstance({required this.id, required this.size, this.roomId});
  final int id;
  final String size;
  final String? roomId;

  factory WidgetInstance.fromJson(Map<Object?, Object?> json) => WidgetInstance(
    id: json['appWidgetId'] as int,
    size: json['size'] as String,
    roomId: json['roomId'] as String?,
  );
}

class RoomWidgetViewData {
  const RoomWidgetViewData({
    required this.title,
    required this.countdownText,
    required this.eventDateTimeText,
    required this.completed,
  });
  final String title;
  final String countdownText;
  final String eventDateTimeText;
  final bool completed;

  factory RoomWidgetViewData.fromData(RoomWidgetData data, DateTime now) =>
      RoomWidgetViewData(
        title: data.title,
        countdownText: countdown(data.eventAt, now),
        eventDateTimeText: compactDateTime(data.eventAt),
        completed: !data.eventAt.isAfter(now.toUtc()),
      );
}
