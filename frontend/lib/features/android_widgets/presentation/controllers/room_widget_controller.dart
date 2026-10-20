import 'package:flutter/foundation.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/android_widgets/domain/room_widget_data.dart';
import 'package:hyped/features/rooms/domain/room.dart';

class RoomWidgetController extends ChangeNotifier {
  RoomWidgetController(this._repository, [this._fetchRoom]);
  final RoomWidgetRepository _repository;
  final Future<Room> Function(String)? _fetchRoom;
  bool _loading = false;
  bool _disposed = false;
  List<WidgetInstance> _instances = [];
  int? _selectedId;
  String? _message;
  bool get loading => _loading;
  List<WidgetInstance> get instances => _instances;
  int? get selectedId => _selectedId;
  String? get selectedRoomId => _instances
      .where((widget) => widget.id == _selectedId)
      .firstOrNull
      ?.roomId;
  String? get message => _message;

  void chooseWidget(int id) {
    if (!_instances.any((widget) => widget.id == id)) return;
    _selectedId = id;
    notifyListeners();
  }

  Future<void> load({int? preferredId}) async {
    await _perform(() async {
      _instances = await _repository.instances();
      final wanted = preferredId ?? _selectedId;
      _selectedId = _instances.any((widget) => widget.id == wanted)
          ? wanted
          : _instances.firstOrNull?.id;
    });
  }

  Future<void> select(Room room) async {
    final id = _selectedId;
    final epoch = _repository.sessionEpoch;
    if (id == null) return;
    await _perform(() async {
      final current = await _fetchRoom?.call(room.id) ?? room;
      if (current.status != RoomStatus.active) {
        throw StateError('This room is unavailable for selection.');
      }
      await _repository.selectRoom(id, current, epoch: epoch);
      _instances = await _repository.instances();
      _message = 'Widget updated.';
    });
  }

  Future<void> clear() async {
    final id = _selectedId;
    if (id == null) return;
    await _perform(() async {
      await _repository.clearSelection(id);
      _instances = await _repository.instances();
      _message = 'Widget room cleared.';
    });
  }

  Future<void> _perform(Future<void> Function() operation) async {
    if (_loading || _disposed) return;
    _loading = true;
    _message = null;
    notifyListeners();
    try {
      await operation();
    } catch (_) {
      _message = 'Could not update widget settings. Please retry.';
    } finally {
      _loading = false;
      if (!_disposed) notifyListeners();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
