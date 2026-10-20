import 'package:flutter/foundation.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/rooms/domain/room.dart';

class RoomWidgetController extends ChangeNotifier {
  RoomWidgetController(this._repository);

  final RoomWidgetRepository _repository;
  bool _loading = false;
  String? _selectedRoomId;
  String? _message;

  bool get loading => _loading;
  String? get selectedRoomId => _selectedRoomId;
  String? get message => _message;

  Future<void> load() async {
    _loading = true;
    _message = null;
    notifyListeners();
    try {
      _selectedRoomId = await _repository.selectedRoomId();
    } catch (_) {
      _message = 'Could not load widget settings.';
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  Future<void> select(Room room) async {
    _loading = true;
    _message = null;
    notifyListeners();
    try {
      await _repository.cacheRoom(room);
      await _repository.selectRoom(room.id);
      _selectedRoomId = room.id;
      _message = 'Widget updated for ${room.title}.';
    } catch (_) {
      _message = 'Could not update the widget.';
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  Future<void> clear() async {
    _loading = true;
    _message = null;
    notifyListeners();
    try {
      await _repository.clearSelection();
      _selectedRoomId = null;
      _message = 'Widget room cleared.';
    } catch (_) {
      _message = 'Could not clear the widget.';
    } finally {
      _loading = false;
      notifyListeners();
    }
  }
}
