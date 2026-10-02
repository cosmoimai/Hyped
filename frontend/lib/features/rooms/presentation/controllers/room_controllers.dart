import 'package:flutter/foundation.dart';
import 'package:hyped/features/rooms/data/room_repository.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_commands.dart';

class RoomListController extends ChangeNotifier {
  RoomListController(this._repository);

  final RoomRepository _repository;
  bool _loading = false;
  String? _error;
  List<Room> _rooms = const [];

  bool get loading => _loading;
  String? get error => _error;
  List<Room> get rooms => _rooms;
  Room? get nearestRoom => _rooms.isEmpty ? null : _rooms.first;
  List<Room> get otherRooms =>
      _rooms.length <= 1 ? const [] : _rooms.skip(1).toList(growable: false);

  Future<void> load() async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final rooms = await _repository.listRooms();
      _rooms = [...rooms]..sort((a, b) => a.eventAt.compareTo(b.eventAt));
    } catch (_) {
      _error = 'Could not load your rooms. Pull to retry.';
    } finally {
      _loading = false;
      notifyListeners();
    }
  }
}

class RoomDetailController extends ChangeNotifier {
  RoomDetailController(this._repository, this.roomId);

  final RoomRepository _repository;
  final String roomId;
  bool _loading = false;
  bool _saving = false;
  String? _error;
  Room? _room;

  bool get loading => _loading;
  bool get saving => _saving;
  String? get error => _error;
  Room? get room => _room;

  Future<void> load() async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      _room = await _repository.getRoom(roomId);
    } catch (_) {
      _error = 'Could not load this room.';
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  Future<bool> save(RoomDraft draft) async {
    final current = _room;
    if (current == null) return false;
    final validation = RoomDraftValidation.validate(draft, DateTime.now());
    if (validation != null) {
      _error = validation;
      notifyListeners();
      return false;
    }
    _saving = true;
    _error = null;
    notifyListeners();
    try {
      _room = await _repository.updateRoom(current, draft);
      return true;
    } catch (_) {
      _error = 'Could not save room changes.';
      return false;
    } finally {
      _saving = false;
      notifyListeners();
    }
  }
}

class RoomCreationController extends ChangeNotifier {
  RoomCreationController(this._repository);

  final RoomRepository _repository;
  bool _submitting = false;
  String? _error;
  Room? _created;

  bool get submitting => _submitting;
  String? get error => _error;
  Room? get created => _created;

  Future<Room?> submit(RoomDraft draft) async {
    final validation = RoomDraftValidation.validate(draft, DateTime.now());
    if (validation != null) {
      _error = validation;
      notifyListeners();
      return null;
    }
    _submitting = true;
    _error = null;
    notifyListeners();
    try {
      _created = await _repository.createRoom(draft);
      return _created;
    } catch (_) {
      _error = 'Could not create the room. Please try again.';
      return null;
    } finally {
      _submitting = false;
      notifyListeners();
    }
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }
}
