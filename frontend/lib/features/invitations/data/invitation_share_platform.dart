import 'package:flutter/services.dart';
import 'package:hyped/features/invitations/domain/share_invitation.dart';

class InvitationSharePlatform {
  const InvitationSharePlatform();
  static const _channel = MethodChannel('hyped/invitation_share');

  Future<void> share(ShareInvitation invitation) async {
    await _channel.invokeMethod<void>('share', {
      'roomCode': invitation.roomCode,
      'inviteUrl': invitation.inviteUrl,
    });
  }
}
