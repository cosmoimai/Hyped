import 'package:hyped/features/invitations/data/invitation_api.dart';
import 'package:hyped/features/invitations/domain/invitation.dart';

class InvitationRepository {
  InvitationRepository(this._api);

  final InvitationApi _api;

  Future<InvitePreview> preview(PendingInviteIntent intent) =>
      switch (intent.kind) {
        InviteKind.link => _api.previewInvitation(intent.credential),
        InviteKind.roomCode => _api.previewRoomCode(intent.credential),
      };

  Future<InviteJoinResult> join(
    PendingInviteIntent intent,
    String previewReference,
  ) => switch (intent.kind) {
    InviteKind.link => _api.joinInvitation(previewReference),
    InviteKind.roomCode => _api.joinRoomCode(previewReference),
  };
}
