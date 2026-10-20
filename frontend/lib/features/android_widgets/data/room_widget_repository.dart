import 'package:flutter/services.dart';
import 'package:hyped/features/android_widgets/domain/room_widget_data.dart';
import 'package:hyped/features/rooms/domain/room.dart';

abstract interface class RoomWidgetRepository {
  Future<void> cacheRooms(List<Room> rooms);
  Future<void> cacheRoom(Room room);
  Future<String?> selectedRoomId();
  Future<void> selectRoom(String roomId);
  Future<void> clearSelection();
}

class NoopRoomWidgetRepository implements RoomWidgetRepository {
  const NoopRoomWidgetRepository();

  @override
  Future<void> cacheRooms(List<Room> rooms) async {}

  @override
  Future<void> cacheRoom(Room room) async {}

  @override
  Future<String?> selectedRoomId() async => null;

  @override
  Future<void> selectRoom(String roomId) async {}

  @override
  Future<void> clearSelection() async {}
}

class MethodChannelRoomWidgetRepository implements RoomWidgetRepository {
  MethodChannelRoomWidgetRepository([
    this._channel = const MethodChannel('hyped/android_widgets'),
  ]);

  final MethodChannel _channel;

  @override
  Future<void> cacheRooms(List<Room> rooms) async {
    await _channel.invokeMethod<void>(
      'cacheRooms',
      rooms.map((room) => RoomWidgetData.fromRoom(room).toJson()).toList(),
    );
  }

  @override
  Future<void> cacheRoom(Room room) async {
    await _channel.invokeMethod<void>(
      'cacheRoom',
      RoomWidgetData.fromRoom(room).toJson(),
    );
  }

  @override
  Future<String?> selectedRoomId() =>
      _channel.invokeMethod<String>('selectedRoomId');

  @override
  Future<void> selectRoom(String roomId) =>
      _channel.invokeMethod<void>('selectRoom', roomId);

  @override
  Future<void> clearSelection() =>
      _channel.invokeMethod<void>('clearSelection');
}
