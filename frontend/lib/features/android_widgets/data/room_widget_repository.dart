import 'package:flutter/services.dart';
import 'package:hyped/features/android_widgets/domain/room_widget_data.dart';
import 'package:hyped/features/rooms/domain/room.dart';

abstract interface class RoomWidgetRepository {
  int get sessionEpoch;
  Future<void> startSession();
  Future<void> clearProtectedContent();
  Future<List<WidgetInstance>> instances();
  Future<void> cacheRooms(List<Room> rooms, {required int epoch});
  Future<void> cacheRoom(Room room, {required int epoch});
  Future<void> markRoom(String roomId, String state, {required int epoch});
  Future<void> selectRoom(int appWidgetId, Room room, {required int epoch});
  Future<void> clearSelection(int appWidgetId);
}

class NoopRoomWidgetRepository implements RoomWidgetRepository {
  const NoopRoomWidgetRepository();
  @override
  int get sessionEpoch => 0;
  @override
  Future<void> startSession() async {}
  @override
  Future<void> clearProtectedContent() async {}
  @override
  Future<List<WidgetInstance>> instances() async => [];
  @override
  Future<void> cacheRooms(List<Room> rooms, {required int epoch}) async {}
  @override
  Future<void> cacheRoom(Room room, {required int epoch}) async {}
  @override
  Future<void> markRoom(
    String roomId,
    String state, {
    required int epoch,
  }) async {}
  @override
  Future<void> selectRoom(
    int appWidgetId,
    Room room, {
    required int epoch,
  }) async {}
  @override
  Future<void> clearSelection(int appWidgetId) async {}
}

class MethodChannelRoomWidgetRepository implements RoomWidgetRepository {
  MethodChannelRoomWidgetRepository([
    this._channel = const MethodChannel('hyped/android_widgets'),
  ]);
  final MethodChannel _channel;
  int _epoch = 0;
  bool _enabled = false;
  @override
  int get sessionEpoch => _epoch;

  @override
  Future<void> startSession() async {
    _epoch++;
    _enabled = true;
  }

  @override
  Future<void> clearProtectedContent() async {
    // Fence pending HTTP responses before the first asynchronous boundary.
    _enabled = false;
    _epoch++;
    await _channel.invokeMethod<void>('clearProtectedContent');
  }

  bool _current(int epoch) => _enabled && epoch == _epoch;

  @override
  Future<List<WidgetInstance>> instances() async {
    final items = await _channel.invokeListMethod<Object?>('listWidgets') ?? [];
    return items
        .whereType<Map>()
        .map(
          (item) => WidgetInstance.fromJson(Map<Object?, Object?>.from(item)),
        )
        .toList();
  }

  @override
  Future<void> cacheRooms(List<Room> rooms, {required int epoch}) async {
    if (!_current(epoch)) return;
    await _channel.invokeMethod<void>(
      'cacheRooms',
      rooms.map((room) => RoomWidgetData.fromRoom(room).toJson()).toList(),
    );
  }

  @override
  Future<void> cacheRoom(Room room, {required int epoch}) async {
    if (!_current(epoch)) return;
    await _channel.invokeMethod<void>(
      'cacheRoom',
      RoomWidgetData.fromRoom(room).toJson(),
    );
  }

  @override
  Future<void> markRoom(
    String roomId,
    String state, {
    required int epoch,
  }) async {
    if (!_current(epoch)) return;
    await _channel.invokeMethod<void>('markRoom', {
      'roomId': roomId,
      'state': state,
    });
  }

  @override
  Future<void> selectRoom(
    int appWidgetId,
    Room room, {
    required int epoch,
  }) async {
    if (!_current(epoch)) {
      throw StateError('Sign in again to configure this widget.');
    }
    await _channel.invokeMethod<void>('selectRoom', {
      'appWidgetId': appWidgetId,
      'room': RoomWidgetData.fromRoom(room).toJson(),
    });
  }

  @override
  Future<void> clearSelection(int appWidgetId) => _channel.invokeMethod<void>(
    'clearSelection',
    {'appWidgetId': appWidgetId},
  );
}
