import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_formatters.dart';

class RoomWidgetData {
  const RoomWidgetData({
    required this.roomId,
    required this.title,
    required this.eventAt,
    required this.eventTimeZone,
    required this.syncedAt,
  });

  final String roomId;
  final String title;
  final DateTime eventAt;
  final String eventTimeZone;
  final DateTime syncedAt;

  factory RoomWidgetData.fromRoom(Room room, {DateTime? syncedAt}) =>
      RoomWidgetData(
        roomId: room.id,
        title: room.title,
        eventAt: room.eventAt,
        eventTimeZone: room.eventTimeZone,
        syncedAt: syncedAt ?? DateTime.now().toUtc(),
      );

  Map<String, Object?> toJson() => {
    'roomId': roomId,
    'title': title,
    'eventAt': eventAt.toUtc().toIso8601String(),
    'eventTimeZone': eventTimeZone,
    'syncedAt': syncedAt.toUtc().toIso8601String(),
  };

  factory RoomWidgetData.fromJson(Map<String, Object?> json) => RoomWidgetData(
    roomId: json['roomId'] as String,
    title: json['title'] as String,
    eventAt: DateTime.parse(json['eventAt'] as String).toUtc(),
    eventTimeZone: json['eventTimeZone'] as String,
    syncedAt: DateTime.parse(json['syncedAt'] as String).toUtc(),
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

  factory RoomWidgetViewData.fromData(RoomWidgetData data, DateTime now) {
    final completed = !data.eventAt.isAfter(now.toUtc());
    return RoomWidgetViewData(
      title: data.title,
      countdownText: countdown(data.eventAt, now),
      eventDateTimeText: compactDateTime(data.eventAt),
      completed: completed,
    );
  }
}
