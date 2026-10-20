import 'package:flutter/foundation.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/android_widgets/data/widget_refresh_coordinator.dart';
import 'package:hyped/features/rooms/data/member_api.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/domain/room_member.dart';

class MemberController extends ChangeNotifier {
  MemberController(this.api, this.roomId, [this.widgets]);
  final MemberApi api;
  final String roomId;
  final RoomWidgetRepository? widgets;
  MemberSnapshot? snapshot;
  String? error;
  bool loading = false;
  bool saving = false;
  bool _disposed = false;
  bool _stale = false;
  bool get stale => _stale;
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  Future<void> load() async {
    if (loading || saving) return;
    loading = true;
    error = null;
    _notify();
    final epoch = widgets?.sessionEpoch ?? 0;
    try {
      final result = await api.load(roomId);
      if (_disposed || (widgets != null && epoch != widgets!.sessionEpoch)) {
        return;
      }
      snapshot = result;
      _stale = false;
    } catch (failure) {
      await _failure(failure, epoch);
    } finally {
      loading = false;
      _notify();
    }
  }

  Future<bool> change(RoomMember member, MemberAction action) async {
    final current = snapshot;
    if (_disposed || loading || saving || _stale || current == null) {
      return false;
    }
    final targets = current.items.where((item) => item.userId == member.userId);
    if (targets.isEmpty ||
        !memberActions(current, targets.first).contains(action)) {
      return false;
    }
    final epoch = widgets?.sessionEpoch ?? 0;
    saving = true;
    error = null;
    _notify();
    var succeeded = false;
    try {
      await api.change(roomId, member.userId, action, current.etag);
      succeeded = true;
      // Clear stale permissions immediately, particularly after ownership transfer.
      snapshot = null;
      final result = await api.load(roomId);
      if (!_disposed && (widgets == null || epoch == widgets!.sessionEpoch)) {
        snapshot = result;
        _stale = false;
      }
    } catch (failure) {
      await _failure(failure, epoch, accessCheck: succeeded);
    } finally {
      saving = false;
      _notify();
    }
    return succeeded;
  }

  Future<void> _failure(
    Object failure,
    int epoch, {
    bool accessCheck = true,
  }) async {
    if (_disposed || (widgets != null && epoch != widgets!.sessionEpoch)) {
      return;
    }
    _stale = true;
    if (failure is RoomApiException) {
      if (failure.statusCode == 401 ||
          failure.statusCode == 403 ||
          failure.statusCode == 404 ||
          failure.statusCode == 410) {
        snapshot = null;
      }
      error = memberErrorMessage(failure);
      if (accessCheck) {
        await WidgetRefreshCoordinator(
          widgets,
        ).afterFailure(roomId, failure, epoch);
      }
    } else {
      error = 'Could not update members. Check your connection and reload.';
    }
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}

String memberErrorMessage(RoomApiException error) => switch (error.code) {
  'ROOM_REVISION_MISMATCH' || 'ROOM_REVISION_REQUIRED' =>
    'This room changed. Reload members before trying again.',
  'ROOM_ARCHIVED' => 'Archived rooms cannot be changed.',
  'OWNED_ROOM_LIMIT_REACHED' =>
    'The selected member cannot own another active room.',
  'JOINED_ROOM_LIMIT_REACHED' =>
    'Ownership cannot change because a room limit was reached.',
  'INVALID_OWNER' ||
  'VALIDATION_FAILED' ||
  'MALFORMED_REQUEST' => 'Check the selected member and action.',
  _ => switch (error.statusCode) {
    401 => 'Your session expired. Sign in again.',
    403 => 'This action is unavailable for your current role.',
    404 || 410 => 'This room or member is no longer available.',
    409 => 'This change conflicts with the current room. Reload members.',
    412 || 428 => 'This room changed. Reload members before trying again.',
    _ => 'Could not update members. Check your connection and reload.',
  },
};
