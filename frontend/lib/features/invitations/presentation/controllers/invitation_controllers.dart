import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:hyped/core/offline/offline_state.dart';
import 'package:flutter/foundation.dart';
import 'package:hyped/features/invitations/data/invitation_api.dart';
import 'package:hyped/features/invitations/data/invitation_repository.dart';
import 'package:hyped/features/invitations/domain/invitation.dart';

class PendingInviteController extends ChangeNotifier {
  PendingInviteController({this.onClear, DateTime Function()? clock})
    : _clock = clock ?? DateTime.now;
  final DateTime Function() _clock;
  final void Function()? onClear;
  PendingInviteIntent? _intent;
  final _delivered = <String, DateTime>{};
  int _generation = 0;
  PendingInviteIntent? get intent => _intent;
  int get generation => _generation;

  void remember(PendingInviteIntent intent) {
    if (_intent == intent) return;
    _intent = intent;
    _generation++;
    notifyListeners();
  }

  String _fingerprint(PendingInviteIntent intent) =>
      sha256.convert(utf8.encode(intent.credential)).toString();
  void _markDelivered(PendingInviteIntent intent) {
    _delivered.removeWhere(
      (_, at) => _clock().difference(at) >= const Duration(seconds: 30),
    );
    final digest = _fingerprint(intent);
    if (_delivered.length == 16 && !_delivered.containsKey(digest)) {
      _delivered.remove(_delivered.keys.first);
    }
    _delivered[digest] = _clock();
  }

  bool acceptExternal(PendingInviteIntent intent) {
    if (intent.kind != InviteKind.link || _intent == intent) return false;
    final previous = _delivered[_fingerprint(intent)];
    if (previous != null &&
        _clock().difference(previous) < const Duration(seconds: 30)) {
      return false;
    }
    _markDelivered(intent);
    remember(intent);
    return true;
  }

  void complete(PendingInviteIntent intent) {
    if (_intent != intent) return;
    _intent = null;
    _generation++;
    if (intent.kind == InviteKind.link) _markDelivered(intent);
    // Keep only short-lived bounded digests, never a consumed raw token.
    onClear?.call();
    notifyListeners();
  }

  void clear() {
    final changed = _intent != null;
    _intent = null;
    _delivered.clear();
    _generation++;
    onClear?.call();
    if (changed) notifyListeners();
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
    if (_disposed) return null;
    if (_result != null) return _result;
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
  'OFFLINE_READ_ONLY' => offlineReadOnlyMessage,
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
