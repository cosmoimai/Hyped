import 'package:hyped/core/offline/offline_state.dart';
import 'package:hyped/features/rooms/data/cache/cached_room_repository.dart';
import 'package:flutter/foundation.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/android_widgets/data/widget_refresh_coordinator.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/data/room_repository.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_commands.dart';

class RoomListController extends ChangeNotifier {
  RoomListController(this._repository, [this._widgets]);

  final RoomRepository _repository;
  final RoomWidgetRepository? _widgets;
  bool _disposed = false;
  bool _loading = false;
  String? _error;
  List<Room> _rooms = const [];
  DateTime? lastSync;
  bool usingCache = false;

  bool get loading => _loading;
  String? get error => _error;
  List<Room> get rooms => _rooms;
  Room? get nearestRoom =>
      _rooms.where((room) => room.status == RoomStatus.active).firstOrNull;
  List<Room> get otherRooms => _rooms
      .where((room) => room.id != nearestRoom?.id)
      .toList(growable: false);

  Future<void> load() async {
    if (_disposed || _loading) return;
    final epoch = _widgets?.sessionEpoch ?? 0;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      if (_repository case final OfflineRoomReads cached) {
        final snapshot = await cached.cachedList();
        if (_disposed || (_widgets != null && epoch != _widgets.sessionEpoch)) {
          return;
        }
        if (snapshot != null) {
          _rooms = snapshot.rooms;
          lastSync = snapshot.syncedAt;
          usingCache = true;
          notifyListeners();
        }
      }
      final rooms = await _repository.listRooms();
      if (_disposed || (_widgets != null && epoch != _widgets.sessionEpoch)) {
        return;
      }
      _rooms =
          rooms.where((room) => room.status != RoomStatus.deleting).toList()
            ..sort((a, b) => a.eventAt.compareTo(b.eventAt));
      usingCache = false;
      if (_repository case final OfflineRoomReads cached) {
        lastSync = cached.lastListSync;
      }
      await WidgetRefreshCoordinator(
        _widgets,
      ).afterList(_rooms, _repository.getRoom, epoch);
    } catch (failure) {
      if (_disposed || (_widgets != null && epoch != _widgets.sessionEpoch)) {
        return;
      }
      if (failure is RoomApiException &&
          ([401, 403].contains(failure.statusCode) ||
              failure.code == 'CACHE_CHANGED')) {
        _rooms = const [];
        lastSync = null;
      }
      usingCache = lastSync != null;
      _error =
          failure is RoomApiException && failure.code == 'OFFLINE_READ_ONLY'
          ? offlineReadOnlyMessage
          : 'Could not refresh your rooms. Pull to retry.';
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

class RoomDetailController extends ChangeNotifier {
  RoomDetailController(this._repository, this.roomId, [this._widgets]);

  final RoomRepository _repository;
  final String roomId;
  final RoomWidgetRepository? _widgets;
  bool _disposed = false;
  bool _loading = false;
  bool _saving = false;
  String? _error;
  Room? _room;

  bool get loading => _loading;
  bool get saving => _saving;
  bool get readOnly => _room?.isCached == true || _loading || _error != null;
  String? get error => _error;
  Room? get room => _room;

  Future<void> load() async {
    final epoch = _widgets?.sessionEpoch ?? 0;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      if (_repository case final OfflineRoomReads cached) {
        final snapshot = await cached.cachedDetail(roomId);
        if (_disposed || (_widgets != null && epoch != _widgets.sessionEpoch)) {
          return;
        }
        if (snapshot != null) {
          _room = snapshot;
          notifyListeners();
        }
      }
      final loaded = await _repository.getRoom(roomId);
      if (_disposed || (_widgets != null && epoch != _widgets.sessionEpoch)) {
        return;
      }
      _room = loaded;
      if (_room case final room?) {
        await WidgetRefreshCoordinator(_widgets).afterDetail(room, epoch);
      }
    } catch (error) {
      if (_disposed || (_widgets != null && epoch != _widgets.sessionEpoch)) {
        return;
      }
      if (error is RoomApiException &&
          ([401, 403, 404, 410].contains(error.statusCode) ||
              error.code == 'CACHE_CHANGED')) {
        _room = null;
      }
      await WidgetRefreshCoordinator(
        _widgets,
      ).afterFailure(roomId, error, epoch);
      _error =
          error is RoomApiException &&
              [403, 404, 410].contains(error.statusCode)
          ? 'This room was removed or is no longer accessible.'
          : error is RoomApiException && error.code == 'OFFLINE_READ_ONLY'
          ? offlineReadOnlyMessage
          : 'Could not refresh this room.';
    } finally {
      _loading = false;
      if (!_disposed) notifyListeners();
    }
  }

  Future<bool> save(RoomDraft draft) async {
    final current = _room;
    if (current == null || _disposed) return false;
    if (current.isCached) {
      _error = offlineReadOnlyMessage;
      notifyListeners();
      return false;
    }
    final validation = RoomDraftValidation.validate(draft, DateTime.now());
    if (validation != null) {
      _error = validation;
      if (!_disposed) notifyListeners();
      return false;
    }
    final epoch = _widgets?.sessionEpoch ?? 0;
    _saving = true;
    _error = null;
    notifyListeners();
    try {
      _room = await _repository.updateRoom(current, draft);
      if (_room case final room?) {
        await WidgetRefreshCoordinator(_widgets).afterDetail(room, epoch);
      }
      return true;
    } on RoomApiException catch (error) {
      if ([401, 404, 410].contains(error.statusCode)) {
        _room = null;
      }
      if ([404, 410].contains(error.statusCode)) {
        await WidgetRefreshCoordinator(
          _widgets,
        ).afterFailure(roomId, error, epoch);
      }
      _error = _roomMutationMessage(error);
      return false;
    } catch (_) {
      _error = 'Could not save room changes.';
      return false;
    } finally {
      _saving = false;
      if (!_disposed) notifyListeners();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
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
    } on RoomApiException catch (error) {
      _error = _roomMutationMessage(error);
      return null;
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

String _roomMutationMessage(RoomApiException error) => switch (error.code) {
  'OFFLINE_READ_ONLY' => offlineReadOnlyMessage,
  'VALIDATION_FAILED' => 'Check the room details and try again.',
  'EVENT_TIME_NOT_FUTURE' => 'Choose a future event time.',
  'EVENT_TIME_AMBIGUOUS' =>
    'Choose a different time; that local time is ambiguous.',
  'EVENT_TIME_NONEXISTENT' =>
    'Choose a different time; that local time does not exist.',
  'OWNED_ROOM_LIMIT_REACHED' =>
    'You already own the maximum number of active rooms.',
  'ROOM_REVISION_MISMATCH' =>
    'This room changed. Reload it before editing again.',
  'ROOM_REVISION_REQUIRED' => 'Reload this room before editing again.',
  'ROOM_ACTION_FORBIDDEN' => 'Only owners and co-hosts can edit this room.',
  'ROOM_UNAVAILABLE' => 'This room is no longer available.',
  'ROOM_ARCHIVED' => 'This room is archived. Reload it before editing.',
  _ => 'Could not save this room. Please try again.',
};
