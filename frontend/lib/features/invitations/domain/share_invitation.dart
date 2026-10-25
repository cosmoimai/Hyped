/// Transient credentials. Never persist or include in diagnostics.
class ShareInvitation {
  const ShareInvitation({required this.roomCode, this.inviteUrl});
  final String roomCode;
  final String? inviteUrl;

  factory ShareInvitation.fromJson(Map<String, dynamic> json) {
    final code = json['roomCode'];
    if (code is! String ||
        !RegExp(r'^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$').hasMatch(code)) {
      throw const FormatException('Invalid invitation response.');
    }
    final url = json['inviteUrl'];
    return ShareInvitation(
      roomCode: code,
      inviteUrl: url is String && url.length <= 512 ? url : null,
    );
  }

  @override
  String toString() => 'ShareInvitation[REDACTED]';
}
