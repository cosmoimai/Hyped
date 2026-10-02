class Room {
  const Room({
    required this.id,
    required this.title,
    required this.eventAt,
    required this.eventTimeZone,
    required this.theme,
    required this.status,
    required this.role,
    required this.memberCount,
    required this.revision,
    required this.createdAt,
    required this.updatedAt,
    required this.serverNow,
    this.location,
    this.description,
    this.archivedAt,
    this.deleteAfter,
    this.etag,
  });

  final String id;
  final String title;
  final DateTime eventAt;
  final String eventTimeZone;
  final String? location;
  final String? description;
  final RoomTheme theme;
  final RoomStatus status;
  final RoomRole role;
  final int memberCount;
  final int revision;
  final DateTime? archivedAt;
  final DateTime? deleteAfter;
  final DateTime createdAt;
  final DateTime updatedAt;
  final DateTime serverNow;
  final String? etag;

  bool get canEdit => role == RoomRole.owner || role == RoomRole.coHost;

  Room copyWith({String? etag}) => Room(
    id: id,
    title: title,
    eventAt: eventAt,
    eventTimeZone: eventTimeZone,
    location: location,
    description: description,
    theme: theme,
    status: status,
    role: role,
    memberCount: memberCount,
    revision: revision,
    archivedAt: archivedAt,
    deleteAfter: deleteAfter,
    createdAt: createdAt,
    updatedAt: updatedAt,
    serverNow: serverNow,
    etag: etag ?? this.etag,
  );

  factory Room.fromJson(Map<String, dynamic> json, {String? etag}) => Room(
    id: json['id'] as String,
    title: json['title'] as String,
    eventAt: DateTime.parse(json['eventAt'] as String).toUtc(),
    eventTimeZone: json['eventTimeZone'] as String,
    location: json['location'] as String?,
    description: json['description'] as String?,
    theme: RoomTheme.fromJson(json['theme'] as Map<String, dynamic>),
    status: RoomStatusX.fromWire(json['status'] as String),
    role: RoomRoleX.fromWire(json['role'] as String),
    memberCount: json['memberCount'] as int,
    revision: json['revision'] as int,
    archivedAt: _date(json['archivedAt']),
    deleteAfter: _date(json['deleteAfter']),
    createdAt: DateTime.parse(json['createdAt'] as String).toUtc(),
    updatedAt: DateTime.parse(json['updatedAt'] as String).toUtc(),
    serverNow: DateTime.parse(json['serverNow'] as String).toUtc(),
    etag: etag,
  );
}

class RoomEventTheme {
  const RoomEventTheme({
    required this.id,
    required this.title,
    required this.eventAt,
    required this.eventTimeZone,
    required this.theme,
    required this.revision,
    required this.updatedAt,
    required this.serverNow,
    this.location,
    this.description,
    this.etag,
  });

  final String id;
  final String title;
  final DateTime eventAt;
  final String eventTimeZone;
  final String? location;
  final String? description;
  final RoomTheme theme;
  final int revision;
  final DateTime updatedAt;
  final DateTime serverNow;
  final String? etag;

  factory RoomEventTheme.fromJson(Map<String, dynamic> json, {String? etag}) =>
      RoomEventTheme(
        id: json['id'] as String,
        title: json['title'] as String,
        eventAt: DateTime.parse(json['eventAt'] as String).toUtc(),
        eventTimeZone: json['eventTimeZone'] as String,
        location: json['location'] as String?,
        description: json['description'] as String?,
        theme: RoomTheme.fromJson(json['theme'] as Map<String, dynamic>),
        revision: json['revision'] as int,
        updatedAt: DateTime.parse(json['updatedAt'] as String).toUtc(),
        serverNow: DateTime.parse(json['serverNow'] as String).toUtc(),
        etag: etag,
      );
}

class RoomTheme {
  const RoomTheme({
    required this.kind,
    required this.presetKey,
    required this.overlayKey,
  });

  final RoomThemeKind kind;
  final String presetKey;
  final String overlayKey;

  Map<String, dynamic> toJson() => {
    'kind': kind.wire,
    'presetKey': presetKey,
    'overlayKey': overlayKey,
  };

  factory RoomTheme.fromJson(Map<String, dynamic> json) => RoomTheme(
    kind: RoomThemeKindX.fromWire(json['kind'] as String),
    presetKey: json['presetKey'] as String,
    overlayKey: json['overlayKey'] as String,
  );
}

enum RoomStatus { active, archived, deleting }

enum RoomRole { owner, coHost, member }

enum RoomThemeKind { preset, gradient }

extension RoomStatusX on RoomStatus {
  String get wire => switch (this) {
    RoomStatus.active => 'ACTIVE',
    RoomStatus.archived => 'ARCHIVED',
    RoomStatus.deleting => 'DELETING',
  };

  static RoomStatus fromWire(String value) => switch (value) {
    'ACTIVE' => RoomStatus.active,
    'ARCHIVED' => RoomStatus.archived,
    'DELETING' => RoomStatus.deleting,
    _ => throw FormatException('Unknown room status: $value'),
  };
}

extension RoomRoleX on RoomRole {
  String get wire => switch (this) {
    RoomRole.owner => 'OWNER',
    RoomRole.coHost => 'CO_HOST',
    RoomRole.member => 'MEMBER',
  };

  static RoomRole fromWire(String value) => switch (value) {
    'OWNER' => RoomRole.owner,
    'CO_HOST' => RoomRole.coHost,
    'MEMBER' => RoomRole.member,
    _ => throw FormatException('Unknown room role: $value'),
  };
}

extension RoomThemeKindX on RoomThemeKind {
  String get wire => switch (this) {
    RoomThemeKind.preset => 'PRESET',
    RoomThemeKind.gradient => 'GRADIENT',
  };

  static RoomThemeKind fromWire(String value) => switch (value) {
    'PRESET' => RoomThemeKind.preset,
    'GRADIENT' => RoomThemeKind.gradient,
    _ => throw FormatException('Unknown room theme kind: $value'),
  };
}

DateTime? _date(Object? value) =>
    value == null ? null : DateTime.parse(value as String).toUtc();
