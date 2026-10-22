import 'package:flutter/foundation.dart';
import 'package:hyped/features/invitations/data/invitation_api.dart';
import 'package:hyped/features/invitations/data/invitation_repository.dart';
import 'package:hyped/features/invitations/domain/invitation.dart';

class PendingInviteController extends ChangeNotifier {
  PendingInviteIntent? _intent;

  PendingInviteIntent? get intent => _intent;

  void remember(PendingInviteIntent intent) {
    _intent = intent;
    notifyListeners();
  }

  void clear() {
    if (_intent == null) return;
    _intent = null;
    notifyListeners();
  }
}

class InviteFlowController extends ChangeNotifier {
  InviteFlowController(this._repository, this.intent);

  final InvitationRepository _repository;
  final PendingInviteIntent intent;
  bool _loading = false;
  bool _joining = false;
  bool _disposed = false;
  String? _error;
  String? _message;
  InvitePreview? _preview;
  InviteJoinResult? _result;

  bool get loading => _loading;
  bool get joining => _joining;
  String? get error => _error;
  String? get message => _message;
  InvitePreview? get preview => _preview;
  InviteJoinResult? get result => _result;

  Future<void> loadPreview() async {
    if (_disposed || _loading || _joining) return;
    _preview = null;
    _result = null;
    _loading = true;
    _error = null;
    _message = null;
    notifyListeners();
    try {
      _preview = await _repository.preview(intent);
    } on InvitationApiException catch (error) {
      _error = _previewMessage(error);
    } catch (_) {
      _error = 'Could not load this invite. Please try again.';
    } finally {
      _loading = false;
      if (!_disposed) notifyListeners();
    }
  }

  Future<InviteJoinResult?> join() async {
    final preview = _preview;
    if (_disposed || preview == null || _loading || _joining) return null;
    _joining = true;
    _error = null;
    _message = null;
    notifyListeners();
    try {
      _result = await _repository.join(intent, preview.previewReference);
      if (_result!.created) {
        _message = 'Joined room.';
      } else {
        _message = 'You are already in this room.';
      }
      return _result;
    } on InvitationApiException catch (error) {
      _error = _joinMessage(error);
      return null;
    } catch (_) {
      _error = 'Could not join this room. Please try again.';
      return null;
    } finally {
      _joining = false;
      if (!_disposed) notifyListeners();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}

String _previewMessage(InvitationApiException error) => switch (error.code) {
  'INVITATION_INVALID' => 'This invite is invalid, expired, or was rotated.',
  'ROOM_ENDED' => 'This room is no longer available.',
  _ => 'Could not load this invite. Please try again.',
};

String _joinMessage(InvitationApiException error) => switch (error.code) {
  'CURRENT_POLICY_ACCEPTANCE_REQUIRED' =>
    'Review the current legal documents before joining.',
  'INVITATION_INVALID' => 'This invite is invalid, expired, or was rotated.',
  'ROOM_ENDED' => 'This room is no longer available.',
  'ROOM_FULL' => 'This room is full.',
  'JOINED_ROOM_LIMIT_REACHED' => 'You have reached the joined-room limit.',
  'PREVIEW_EXPIRED' =>
    'This invite preview expired. Refresh the preview and try again.',
  _ => 'Could not join this room. Please try again.',
};
