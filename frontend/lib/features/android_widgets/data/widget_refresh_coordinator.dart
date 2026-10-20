import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/domain/room.dart';

class WidgetRefreshCoordinator {
  WidgetRefreshCoordinator(this.widgets);
  final RoomWidgetRepository? widgets;

  Future<void> afterList(
    List<Room> rooms,
    Future<Room> Function(String) fetch,
    int epoch,
  ) async {
    final target = widgets;
    if (target == null || epoch != target.sessionEpoch) return;
    try {
      await target.cacheRooms(rooms, epoch: epoch);
      final selected = await target.instances();
      final missing =
          selected.map((widget) => widget.roomId).whereType<String>().toSet()
            ..removeAll(rooms.map((room) => room.id));
      for (final id in missing) {
        if (epoch != target.sessionEpoch) return;
        try {
          await target.cacheRoom(await fetch(id), epoch: epoch);
        } catch (error) {
          await afterFailure(id, error, epoch);
        }
      }
    } catch (_) {
      // Widget failures must never turn a successful foreground room load into an error.
    }
  }

  Future<void> afterDetail(Room room, int epoch) async {
    try {
      await widgets?.cacheRoom(room, epoch: epoch);
    } catch (_) {}
  }

  Future<void> afterFailure(String id, Object error, int epoch) async {
    if (error is! RoomApiException) {
      return;
    } // Offline/timeouts keep the successful snapshot.
    final state = switch (error.statusCode) {
      404 when error.code == 'ROOM_UNAVAILABLE' => 'unavailable',
      404 || 410 => 'removed',
      403 => 'unavailable',
      _ => null,
    };
    if (state == null) return;
    try {
      await widgets?.markRoom(id, state, epoch: epoch);
    } catch (_) {}
  }
}
