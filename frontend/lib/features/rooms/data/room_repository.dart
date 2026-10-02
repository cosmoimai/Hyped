import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_commands.dart';

class RoomRepository {
  RoomRepository(this._api);

  final RoomApi _api;

  Future<List<Room>> listRooms() => _api.listRooms(status: RoomStatus.active);

  Future<Room> getRoom(String roomId) => _api.getRoom(roomId);

  Future<RoomEventTheme> getEventTheme(String roomId) =>
      _api.getEventTheme(roomId);

  Future<Room> createRoom(RoomDraft draft) => _api.createRoom(draft);

  Future<Room> updateRoom(Room room, RoomDraft draft) {
    final etag = room.etag ?? '"room-${room.revision}"';
    return _api.updateRoom(roomId: room.id, etag: etag, draft: draft);
  }

  Future<RoomEventTheme> updateEventTheme(Room room, RoomDraft draft) {
    final etag = room.etag ?? '"room-${room.revision}"';
    return _api.updateEventTheme(roomId: room.id, etag: etag, draft: draft);
  }
}
