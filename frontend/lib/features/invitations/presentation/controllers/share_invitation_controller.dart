import 'package:flutter/foundation.dart';
import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/invitations/data/invitation_api.dart';
import 'package:hyped/features/invitations/data/invitation_share_platform.dart';
import 'package:hyped/features/invitations/domain/share_invitation.dart';

class ShareInvitationController extends ChangeNotifier {
  ShareInvitationController(
    this.api,
    this.tokens,
    this.platform,
    this.widgets,
    this.roomId,
  );
  final InvitationApi api;
  final TokenStore tokens;
  final InvitationSharePlatform platform;
  final RoomWidgetRepository widgets;
  final String roomId;
  ShareInvitation? invitation;
  String? error;
  bool busy = false;
  bool _disposed = false;
  int _generation = 0;

  Future<void> load() => _run(false);
  Future<void> share() => _run(true);

  Future<void> _run(bool openShare) async {
    if (_disposed || busy) return;
    busy = true;
    error = null;
    invitation = null;
    final generation = ++_generation;
    final epoch = widgets.sessionEpoch;
    notifyListeners();
    bool current() =>
        !_disposed &&
        generation == _generation &&
        epoch == widgets.sessionEpoch;
    try {
      final session = (await tokens.read())?.sessionId;
      if (!current()) return;
      if (session == null) throw const InvitationApiException(statusCode: 401);
      // Fresh authorization on every share, including after rotation/removal.
      final result = await api.currentInvitation(roomId, session);
      final active = (await tokens.read())?.sessionId;
      if (!current()) return;
      if (active != session) {
        throw const InvitationApiException(statusCode: 401);
      }
      if (openShare) {
        await platform.share(result);
      }
      if (current()) invitation = result;
    } catch (failure) {
      if (!current()) return;
      invitation = null;
      error = switch (failure) {
        InvitationApiException(statusCode: 401) =>
          'Sign in again to share this room.',
        InvitationApiException(statusCode: 403 || 404 || 410) =>
          'Invitations are unavailable. This room may be archived or you may no longer have access.',
        InvitationApiException(code: 'OFFLINE_READ_ONLY') =>
          'Connect to the internet to get a current invitation.',
        _ => 'Could not share this invitation. Please try again.',
      };
    } finally {
      if (!_disposed && generation == _generation) {
        if (epoch != widgets.sessionEpoch) {
          invitation = null;
          error = 'Sign in again to share this room.';
        }
        busy = false;
        notifyListeners();
      }
    }
  }

  void invalidate() {
    _generation++;
    invitation = null;
    busy = false;
    error = 'Sign in again to share this room.';
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    invitation = null;
    super.dispose();
  }
}
