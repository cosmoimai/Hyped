import 'package:hyped/features/rooms/domain/room.dart';

class InvitePreview {
  const InvitePreview({
    required this.previewReference,
    required this.expiresAt,
    required this.event,
    required this.inviter,
    required this.memberCount,
    required this.requiresAuthentication,
  });

  final String previewReference;
  final DateTime expiresAt;
  final InviteEvent event;
  final InviteInviter inviter;
  final int memberCount;
  final bool requiresAuthentication;

  factory InvitePreview.fromJson(Map<String, dynamic> json) => InvitePreview(
    previewReference: json['previewReference'] as String,
    expiresAt: DateTime.parse(json['expiresAt'] as String).toUtc(),
    event: InviteEvent.fromJson(json['event'] as Map<String, dynamic>),
    inviter: InviteInviter.fromJson(json['inviter'] as Map<String, dynamic>),
    memberCount: json['memberCount'] as int,
    requiresAuthentication: json['requiresAuthentication'] as bool? ?? true,
  );

  @override
  String toString() => 'InvitePreview[REDACTED]';
}

class InviteEvent {
  const InviteEvent({
    required this.title,
    required this.eventAt,
    required this.eventTimeZone,
    required this.theme,
  });

  final String title;
  final DateTime eventAt;
  final String eventTimeZone;
  final RoomTheme theme;

  factory InviteEvent.fromJson(Map<String, dynamic> json) => InviteEvent(
    title: json['title'] as String,
    eventAt: DateTime.parse(json['eventAt'] as String).toUtc(),
    eventTimeZone: json['eventTimeZone'] as String,
    theme: RoomTheme(
      kind: RoomThemeKindX.fromWire(json['theme']['kind'] as String),
      presetKey: json['theme']['presetKey'] as String,
      overlayKey: json['theme']['overlayKey'] as String? ?? 'dark-soft',
    ),
  );
}

class InviteInviter {
  const InviteInviter({required this.displayName});

  final String displayName;

  factory InviteInviter.fromJson(Map<String, dynamic> json) => InviteInviter(
    displayName: json['displayName'] as String? ?? 'A Hyped member',
  );
}

class InviteJoinResult {
  const InviteJoinResult({required this.room, required this.created});

  final Room room;
  final bool created;
}

enum InviteKind { link, roomCode }

class PendingInviteIntent {
  const PendingInviteIntent({required this.kind, required this.credential});

  final InviteKind kind;
  final String credential;

  String get location => switch (kind) {
    InviteKind.link => '/invite/${Uri.encodeComponent(credential)}',
    InviteKind.roomCode => '/join/room-code/${Uri.encodeComponent(credential)}',
  };

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is PendingInviteIntent &&
          other.kind == kind &&
          other.credential == credential;

  @override
  int get hashCode => Object.hash(kind, credential);

  @override
  String toString() => 'PendingInviteIntent[REDACTED]';
}
