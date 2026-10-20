import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/android_widgets/data/widget_refresh_coordinator.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/data/room_repository.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_lifecycle.dart';

class RoomLifecycleController extends ChangeNotifier {
  RoomLifecycleController(this.repository, this.roomId, [this.widgets]);
  final RoomRepository repository;
  final String roomId;
  final RoomWidgetRepository? widgets;
  Room? room;
  String? error;
  String? message;
  bool loading = false;
  bool saving = false;
  bool removed = false;
  bool stale = false;
  bool _disposed = false;
  final _elapsed = Stopwatch();
  Timer? _boundary;
  DateTime get now =>
      (room?.serverNow ?? DateTime.now().toUtc()).add(_elapsed.elapsed);
  List<RoomLifecycleAction> get actions => room == null || stale || removed
      ? const []
      : lifecycleActions(room!, now);
  bool _current(int epoch) =>
      !_disposed && (widgets == null || epoch == widgets!.sessionEpoch);
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  void _setRoom(Room next) {
    room = next;
    stale = false;
    _elapsed
      ..reset()
      ..start();
    _boundary?.cancel();
    final deadline = next.deleteAfter;
    if (next.status == RoomStatus.archived &&
        deadline != null &&
        deadline.isAfter(now)) {
      _boundary = Timer(deadline.difference(now), _notify);
    }
  }

  Future<void> load() async {
    if (_disposed || loading || saving || removed) return;
    final epoch = widgets?.sessionEpoch ?? 0;
    loading = true;
    error = null;
    _notify();
    try {
      final loaded = await repository.getRoom(roomId);
      if (!_current(epoch)) return;
      _setRoom(loaded);
      await WidgetRefreshCoordinator(widgets).afterDetail(loaded, epoch);
    } catch (failure) {
      if (_current(epoch)) await _failure(failure, epoch, accessCheck: true);
    } finally {
      loading = false;
      _notify();
    }
  }

  Future<bool> change(RoomLifecycleAction action) async {
    final current = room;
    if (_disposed ||
        loading ||
        saving ||
        current == null ||
        !actions.contains(action)) {
      return false;
    }
    final epoch = widgets?.sessionEpoch ?? 0;
    saving = true;
    error = null;
    message = null;
    _notify();
    try {
      final updated = await repository.lifecycle(current, action);
      if (!_current(epoch)) return false;
      if (updated == null) {
        room = null;
        removed = true;
        _boundary?.cancel();
        message = action == RoomLifecycleAction.leave
            ? 'You left this room.'
            : 'Room permanently deleted.';
        try {
          await widgets?.markRoom(roomId, 'removed', epoch: epoch);
        } catch (_) {}
      } else {
        _setRoom(updated);
        await WidgetRefreshCoordinator(widgets).afterDetail(updated, epoch);
        message = action == RoomLifecycleAction.archive
            ? 'Room archived.'
            : 'Room restored.';
      }
      return true;
    } catch (failure) {
      if (_current(epoch)) await _failure(failure, epoch, accessCheck: false);
      return false;
    } finally {
      saving = false;
      _notify();
    }
  }

  Future<void> _failure(
    Object failure,
    int epoch, {
    required bool accessCheck,
  }) async {
    stale = true;
    error = failure is RoomApiException
        ? lifecycleError(failure)
        : 'Could not update this room. Check your connection and reload.';
    if (failure is RoomApiException &&
        (accessCheck ||
            failure.statusCode == 404 ||
            failure.statusCode == 410)) {
      if ([401, 403, 404, 410].contains(failure.statusCode)) room = null;
      await WidgetRefreshCoordinator(
        widgets,
      ).afterFailure(roomId, failure, epoch);
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _boundary?.cancel();
    _elapsed.stop();
    super.dispose();
  }
}

String lifecycleError(RoomApiException error) => switch (error.code) {
  'OWNERSHIP_TRANSFER_REQUIRED' =>
    'Transfer ownership from Members before leaving.',
  'ROOM_RECOVERY_EXPIRED' =>
    'The 24-hour recovery period has ended. Reload this room.',
  'ROOM_DELETE_NOT_ELIGIBLE' =>
    'Permanent deletion is available only after the 24-hour recovery period.',
  'ROOM_ARCHIVED' => 'This room is archived. Reload its current state.',
  'ROOM_MEMBERS_CHANGED' ||
  'ROOM_REVISION_MISMATCH' ||
  'ROOM_REVISION_REQUIRED' =>
    'This room changed. Reload it before trying again.',
  'OWNED_ROOM_LIMIT_REACHED' || 'JOINED_ROOM_LIMIT_REACHED' =>
    'Restoring this room would exceed an active-room limit. Free a room slot and reload.',
  _ => switch (error.statusCode) {
    401 => 'Your session expired. Sign in again.',
    403 =>
      'This action is unavailable for your current role. Reload this room.',
    404 || 410 => 'This room was removed or is no longer accessible.',
    412 || 428 => 'This room changed. Reload it before trying again.',
    409 => 'This action conflicts with the current room. Reload it.',
    _ => 'Could not update this room. Check your connection and reload.',
  },
};
