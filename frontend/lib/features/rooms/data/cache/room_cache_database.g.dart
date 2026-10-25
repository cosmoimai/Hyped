// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'room_cache_database.dart';

// ignore_for_file: type=lint
class $CachedRoomsTable extends CachedRooms
    with TableInfo<$CachedRoomsTable, CachedRoom> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CachedRoomsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _accountIdMeta = const VerificationMeta(
    'accountId',
  );
  @override
  late final GeneratedColumn<String> accountId = GeneratedColumn<String>(
    'account_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _roomIdMeta = const VerificationMeta('roomId');
  @override
  late final GeneratedColumn<String> roomId = GeneratedColumn<String>(
    'room_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _eventAtMeta = const VerificationMeta(
    'eventAt',
  );
  @override
  late final GeneratedColumn<DateTime> eventAt = GeneratedColumn<DateTime>(
    'event_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _eventTimeZoneMeta = const VerificationMeta(
    'eventTimeZone',
  );
  @override
  late final GeneratedColumn<String> eventTimeZone = GeneratedColumn<String>(
    'event_time_zone',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _locationMeta = const VerificationMeta(
    'location',
  );
  @override
  late final GeneratedColumn<String> location = GeneratedColumn<String>(
    'location',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _descriptionMeta = const VerificationMeta(
    'description',
  );
  @override
  late final GeneratedColumn<String> description = GeneratedColumn<String>(
    'description',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _themeKindMeta = const VerificationMeta(
    'themeKind',
  );
  @override
  late final GeneratedColumn<String> themeKind = GeneratedColumn<String>(
    'theme_kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _presetKeyMeta = const VerificationMeta(
    'presetKey',
  );
  @override
  late final GeneratedColumn<String> presetKey = GeneratedColumn<String>(
    'preset_key',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _mediaAssetIdMeta = const VerificationMeta(
    'mediaAssetId',
  );
  @override
  late final GeneratedColumn<String> mediaAssetId = GeneratedColumn<String>(
    'media_asset_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _overlayKeyMeta = const VerificationMeta(
    'overlayKey',
  );
  @override
  late final GeneratedColumn<String> overlayKey = GeneratedColumn<String>(
    'overlay_key',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _roleMeta = const VerificationMeta('role');
  @override
  late final GeneratedColumn<String> role = GeneratedColumn<String>(
    'role',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _memberCountMeta = const VerificationMeta(
    'memberCount',
  );
  @override
  late final GeneratedColumn<int> memberCount = GeneratedColumn<int>(
    'member_count',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _revisionMeta = const VerificationMeta(
    'revision',
  );
  @override
  late final GeneratedColumn<int> revision = GeneratedColumn<int>(
    'revision',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _serverNowMeta = const VerificationMeta(
    'serverNow',
  );
  @override
  late final GeneratedColumn<DateTime> serverNow = GeneratedColumn<DateTime>(
    'server_now',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _syncedAtMeta = const VerificationMeta(
    'syncedAt',
  );
  @override
  late final GeneratedColumn<DateTime> syncedAt = GeneratedColumn<DateTime>(
    'synced_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    accountId,
    roomId,
    title,
    eventAt,
    eventTimeZone,
    location,
    description,
    themeKind,
    presetKey,
    mediaAssetId,
    overlayKey,
    role,
    memberCount,
    revision,
    createdAt,
    updatedAt,
    serverNow,
    syncedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'cached_rooms';
  @override
  VerificationContext validateIntegrity(
    Insertable<CachedRoom> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('account_id')) {
      context.handle(
        _accountIdMeta,
        accountId.isAcceptableOrUnknown(data['account_id']!, _accountIdMeta),
      );
    } else if (isInserting) {
      context.missing(_accountIdMeta);
    }
    if (data.containsKey('room_id')) {
      context.handle(
        _roomIdMeta,
        roomId.isAcceptableOrUnknown(data['room_id']!, _roomIdMeta),
      );
    } else if (isInserting) {
      context.missing(_roomIdMeta);
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('event_at')) {
      context.handle(
        _eventAtMeta,
        eventAt.isAcceptableOrUnknown(data['event_at']!, _eventAtMeta),
      );
    } else if (isInserting) {
      context.missing(_eventAtMeta);
    }
    if (data.containsKey('event_time_zone')) {
      context.handle(
        _eventTimeZoneMeta,
        eventTimeZone.isAcceptableOrUnknown(
          data['event_time_zone']!,
          _eventTimeZoneMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_eventTimeZoneMeta);
    }
    if (data.containsKey('location')) {
      context.handle(
        _locationMeta,
        location.isAcceptableOrUnknown(data['location']!, _locationMeta),
      );
    }
    if (data.containsKey('description')) {
      context.handle(
        _descriptionMeta,
        description.isAcceptableOrUnknown(
          data['description']!,
          _descriptionMeta,
        ),
      );
    }
    if (data.containsKey('theme_kind')) {
      context.handle(
        _themeKindMeta,
        themeKind.isAcceptableOrUnknown(data['theme_kind']!, _themeKindMeta),
      );
    } else if (isInserting) {
      context.missing(_themeKindMeta);
    }
    if (data.containsKey('preset_key')) {
      context.handle(
        _presetKeyMeta,
        presetKey.isAcceptableOrUnknown(data['preset_key']!, _presetKeyMeta),
      );
    } else if (isInserting) {
      context.missing(_presetKeyMeta);
    }
    if (data.containsKey('media_asset_id')) {
      context.handle(
        _mediaAssetIdMeta,
        mediaAssetId.isAcceptableOrUnknown(
          data['media_asset_id']!,
          _mediaAssetIdMeta,
        ),
      );
    }
    if (data.containsKey('overlay_key')) {
      context.handle(
        _overlayKeyMeta,
        overlayKey.isAcceptableOrUnknown(data['overlay_key']!, _overlayKeyMeta),
      );
    } else if (isInserting) {
      context.missing(_overlayKeyMeta);
    }
    if (data.containsKey('role')) {
      context.handle(
        _roleMeta,
        role.isAcceptableOrUnknown(data['role']!, _roleMeta),
      );
    } else if (isInserting) {
      context.missing(_roleMeta);
    }
    if (data.containsKey('member_count')) {
      context.handle(
        _memberCountMeta,
        memberCount.isAcceptableOrUnknown(
          data['member_count']!,
          _memberCountMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_memberCountMeta);
    }
    if (data.containsKey('revision')) {
      context.handle(
        _revisionMeta,
        revision.isAcceptableOrUnknown(data['revision']!, _revisionMeta),
      );
    } else if (isInserting) {
      context.missing(_revisionMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    } else if (isInserting) {
      context.missing(_createdAtMeta);
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_updatedAtMeta);
    }
    if (data.containsKey('server_now')) {
      context.handle(
        _serverNowMeta,
        serverNow.isAcceptableOrUnknown(data['server_now']!, _serverNowMeta),
      );
    } else if (isInserting) {
      context.missing(_serverNowMeta);
    }
    if (data.containsKey('synced_at')) {
      context.handle(
        _syncedAtMeta,
        syncedAt.isAcceptableOrUnknown(data['synced_at']!, _syncedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_syncedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {accountId, roomId};
  @override
  CachedRoom map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return CachedRoom(
      accountId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}account_id'],
      )!,
      roomId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}room_id'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      eventAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}event_at'],
      )!,
      eventTimeZone: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}event_time_zone'],
      )!,
      location: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}location'],
      ),
      description: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}description'],
      ),
      themeKind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}theme_kind'],
      )!,
      presetKey: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}preset_key'],
      )!,
      mediaAssetId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}media_asset_id'],
      ),
      overlayKey: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}overlay_key'],
      )!,
      role: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}role'],
      )!,
      memberCount: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}member_count'],
      )!,
      revision: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}revision'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
      serverNow: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}server_now'],
      )!,
      syncedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}synced_at'],
      )!,
    );
  }

  @override
  $CachedRoomsTable createAlias(String alias) {
    return $CachedRoomsTable(attachedDatabase, alias);
  }
}

class CachedRoom extends DataClass implements Insertable<CachedRoom> {
  final String accountId;
  final String roomId;
  final String title;
  final DateTime eventAt;
  final String eventTimeZone;
  final String? location;
  final String? description;
  final String themeKind;
  final String presetKey;
  final String? mediaAssetId;
  final String overlayKey;
  final String role;
  final int memberCount;
  final int revision;
  final DateTime createdAt;
  final DateTime updatedAt;
  final DateTime serverNow;
  final DateTime syncedAt;
  const CachedRoom({
    required this.accountId,
    required this.roomId,
    required this.title,
    required this.eventAt,
    required this.eventTimeZone,
    this.location,
    this.description,
    required this.themeKind,
    required this.presetKey,
    this.mediaAssetId,
    required this.overlayKey,
    required this.role,
    required this.memberCount,
    required this.revision,
    required this.createdAt,
    required this.updatedAt,
    required this.serverNow,
    required this.syncedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['account_id'] = Variable<String>(accountId);
    map['room_id'] = Variable<String>(roomId);
    map['title'] = Variable<String>(title);
    map['event_at'] = Variable<DateTime>(eventAt);
    map['event_time_zone'] = Variable<String>(eventTimeZone);
    if (!nullToAbsent || location != null) {
      map['location'] = Variable<String>(location);
    }
    if (!nullToAbsent || description != null) {
      map['description'] = Variable<String>(description);
    }
    map['theme_kind'] = Variable<String>(themeKind);
    map['preset_key'] = Variable<String>(presetKey);
    if (!nullToAbsent || mediaAssetId != null) {
      map['media_asset_id'] = Variable<String>(mediaAssetId);
    }
    map['overlay_key'] = Variable<String>(overlayKey);
    map['role'] = Variable<String>(role);
    map['member_count'] = Variable<int>(memberCount);
    map['revision'] = Variable<int>(revision);
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    map['server_now'] = Variable<DateTime>(serverNow);
    map['synced_at'] = Variable<DateTime>(syncedAt);
    return map;
  }

  CachedRoomsCompanion toCompanion(bool nullToAbsent) {
    return CachedRoomsCompanion(
      accountId: Value(accountId),
      roomId: Value(roomId),
      title: Value(title),
      eventAt: Value(eventAt),
      eventTimeZone: Value(eventTimeZone),
      location: location == null && nullToAbsent
          ? const Value.absent()
          : Value(location),
      description: description == null && nullToAbsent
          ? const Value.absent()
          : Value(description),
      themeKind: Value(themeKind),
      presetKey: Value(presetKey),
      mediaAssetId: mediaAssetId == null && nullToAbsent
          ? const Value.absent()
          : Value(mediaAssetId),
      overlayKey: Value(overlayKey),
      role: Value(role),
      memberCount: Value(memberCount),
      revision: Value(revision),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
      serverNow: Value(serverNow),
      syncedAt: Value(syncedAt),
    );
  }

  factory CachedRoom.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return CachedRoom(
      accountId: serializer.fromJson<String>(json['accountId']),
      roomId: serializer.fromJson<String>(json['roomId']),
      title: serializer.fromJson<String>(json['title']),
      eventAt: serializer.fromJson<DateTime>(json['eventAt']),
      eventTimeZone: serializer.fromJson<String>(json['eventTimeZone']),
      location: serializer.fromJson<String?>(json['location']),
      description: serializer.fromJson<String?>(json['description']),
      themeKind: serializer.fromJson<String>(json['themeKind']),
      presetKey: serializer.fromJson<String>(json['presetKey']),
      mediaAssetId: serializer.fromJson<String?>(json['mediaAssetId']),
      overlayKey: serializer.fromJson<String>(json['overlayKey']),
      role: serializer.fromJson<String>(json['role']),
      memberCount: serializer.fromJson<int>(json['memberCount']),
      revision: serializer.fromJson<int>(json['revision']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
      serverNow: serializer.fromJson<DateTime>(json['serverNow']),
      syncedAt: serializer.fromJson<DateTime>(json['syncedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'accountId': serializer.toJson<String>(accountId),
      'roomId': serializer.toJson<String>(roomId),
      'title': serializer.toJson<String>(title),
      'eventAt': serializer.toJson<DateTime>(eventAt),
      'eventTimeZone': serializer.toJson<String>(eventTimeZone),
      'location': serializer.toJson<String?>(location),
      'description': serializer.toJson<String?>(description),
      'themeKind': serializer.toJson<String>(themeKind),
      'presetKey': serializer.toJson<String>(presetKey),
      'mediaAssetId': serializer.toJson<String?>(mediaAssetId),
      'overlayKey': serializer.toJson<String>(overlayKey),
      'role': serializer.toJson<String>(role),
      'memberCount': serializer.toJson<int>(memberCount),
      'revision': serializer.toJson<int>(revision),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
      'serverNow': serializer.toJson<DateTime>(serverNow),
      'syncedAt': serializer.toJson<DateTime>(syncedAt),
    };
  }

  CachedRoom copyWith({
    String? accountId,
    String? roomId,
    String? title,
    DateTime? eventAt,
    String? eventTimeZone,
    Value<String?> location = const Value.absent(),
    Value<String?> description = const Value.absent(),
    String? themeKind,
    String? presetKey,
    Value<String?> mediaAssetId = const Value.absent(),
    String? overlayKey,
    String? role,
    int? memberCount,
    int? revision,
    DateTime? createdAt,
    DateTime? updatedAt,
    DateTime? serverNow,
    DateTime? syncedAt,
  }) => CachedRoom(
    accountId: accountId ?? this.accountId,
    roomId: roomId ?? this.roomId,
    title: title ?? this.title,
    eventAt: eventAt ?? this.eventAt,
    eventTimeZone: eventTimeZone ?? this.eventTimeZone,
    location: location.present ? location.value : this.location,
    description: description.present ? description.value : this.description,
    themeKind: themeKind ?? this.themeKind,
    presetKey: presetKey ?? this.presetKey,
    mediaAssetId: mediaAssetId.present ? mediaAssetId.value : this.mediaAssetId,
    overlayKey: overlayKey ?? this.overlayKey,
    role: role ?? this.role,
    memberCount: memberCount ?? this.memberCount,
    revision: revision ?? this.revision,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
    serverNow: serverNow ?? this.serverNow,
    syncedAt: syncedAt ?? this.syncedAt,
  );
  CachedRoom copyWithCompanion(CachedRoomsCompanion data) {
    return CachedRoom(
      accountId: data.accountId.present ? data.accountId.value : this.accountId,
      roomId: data.roomId.present ? data.roomId.value : this.roomId,
      title: data.title.present ? data.title.value : this.title,
      eventAt: data.eventAt.present ? data.eventAt.value : this.eventAt,
      eventTimeZone: data.eventTimeZone.present
          ? data.eventTimeZone.value
          : this.eventTimeZone,
      location: data.location.present ? data.location.value : this.location,
      description: data.description.present
          ? data.description.value
          : this.description,
      themeKind: data.themeKind.present ? data.themeKind.value : this.themeKind,
      presetKey: data.presetKey.present ? data.presetKey.value : this.presetKey,
      mediaAssetId: data.mediaAssetId.present
          ? data.mediaAssetId.value
          : this.mediaAssetId,
      overlayKey: data.overlayKey.present
          ? data.overlayKey.value
          : this.overlayKey,
      role: data.role.present ? data.role.value : this.role,
      memberCount: data.memberCount.present
          ? data.memberCount.value
          : this.memberCount,
      revision: data.revision.present ? data.revision.value : this.revision,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      serverNow: data.serverNow.present ? data.serverNow.value : this.serverNow,
      syncedAt: data.syncedAt.present ? data.syncedAt.value : this.syncedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('CachedRoom(')
          ..write('accountId: $accountId, ')
          ..write('roomId: $roomId, ')
          ..write('title: $title, ')
          ..write('eventAt: $eventAt, ')
          ..write('eventTimeZone: $eventTimeZone, ')
          ..write('location: $location, ')
          ..write('description: $description, ')
          ..write('themeKind: $themeKind, ')
          ..write('presetKey: $presetKey, ')
          ..write('mediaAssetId: $mediaAssetId, ')
          ..write('overlayKey: $overlayKey, ')
          ..write('role: $role, ')
          ..write('memberCount: $memberCount, ')
          ..write('revision: $revision, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('serverNow: $serverNow, ')
          ..write('syncedAt: $syncedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    accountId,
    roomId,
    title,
    eventAt,
    eventTimeZone,
    location,
    description,
    themeKind,
    presetKey,
    mediaAssetId,
    overlayKey,
    role,
    memberCount,
    revision,
    createdAt,
    updatedAt,
    serverNow,
    syncedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is CachedRoom &&
          other.accountId == this.accountId &&
          other.roomId == this.roomId &&
          other.title == this.title &&
          other.eventAt == this.eventAt &&
          other.eventTimeZone == this.eventTimeZone &&
          other.location == this.location &&
          other.description == this.description &&
          other.themeKind == this.themeKind &&
          other.presetKey == this.presetKey &&
          other.mediaAssetId == this.mediaAssetId &&
          other.overlayKey == this.overlayKey &&
          other.role == this.role &&
          other.memberCount == this.memberCount &&
          other.revision == this.revision &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt &&
          other.serverNow == this.serverNow &&
          other.syncedAt == this.syncedAt);
}

class CachedRoomsCompanion extends UpdateCompanion<CachedRoom> {
  final Value<String> accountId;
  final Value<String> roomId;
  final Value<String> title;
  final Value<DateTime> eventAt;
  final Value<String> eventTimeZone;
  final Value<String?> location;
  final Value<String?> description;
  final Value<String> themeKind;
  final Value<String> presetKey;
  final Value<String?> mediaAssetId;
  final Value<String> overlayKey;
  final Value<String> role;
  final Value<int> memberCount;
  final Value<int> revision;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  final Value<DateTime> serverNow;
  final Value<DateTime> syncedAt;
  final Value<int> rowid;
  const CachedRoomsCompanion({
    this.accountId = const Value.absent(),
    this.roomId = const Value.absent(),
    this.title = const Value.absent(),
    this.eventAt = const Value.absent(),
    this.eventTimeZone = const Value.absent(),
    this.location = const Value.absent(),
    this.description = const Value.absent(),
    this.themeKind = const Value.absent(),
    this.presetKey = const Value.absent(),
    this.mediaAssetId = const Value.absent(),
    this.overlayKey = const Value.absent(),
    this.role = const Value.absent(),
    this.memberCount = const Value.absent(),
    this.revision = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.serverNow = const Value.absent(),
    this.syncedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  CachedRoomsCompanion.insert({
    required String accountId,
    required String roomId,
    required String title,
    required DateTime eventAt,
    required String eventTimeZone,
    this.location = const Value.absent(),
    this.description = const Value.absent(),
    required String themeKind,
    required String presetKey,
    this.mediaAssetId = const Value.absent(),
    required String overlayKey,
    required String role,
    required int memberCount,
    required int revision,
    required DateTime createdAt,
    required DateTime updatedAt,
    required DateTime serverNow,
    required DateTime syncedAt,
    this.rowid = const Value.absent(),
  }) : accountId = Value(accountId),
       roomId = Value(roomId),
       title = Value(title),
       eventAt = Value(eventAt),
       eventTimeZone = Value(eventTimeZone),
       themeKind = Value(themeKind),
       presetKey = Value(presetKey),
       overlayKey = Value(overlayKey),
       role = Value(role),
       memberCount = Value(memberCount),
       revision = Value(revision),
       createdAt = Value(createdAt),
       updatedAt = Value(updatedAt),
       serverNow = Value(serverNow),
       syncedAt = Value(syncedAt);
  static Insertable<CachedRoom> custom({
    Expression<String>? accountId,
    Expression<String>? roomId,
    Expression<String>? title,
    Expression<DateTime>? eventAt,
    Expression<String>? eventTimeZone,
    Expression<String>? location,
    Expression<String>? description,
    Expression<String>? themeKind,
    Expression<String>? presetKey,
    Expression<String>? mediaAssetId,
    Expression<String>? overlayKey,
    Expression<String>? role,
    Expression<int>? memberCount,
    Expression<int>? revision,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<DateTime>? serverNow,
    Expression<DateTime>? syncedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (accountId != null) 'account_id': accountId,
      if (roomId != null) 'room_id': roomId,
      if (title != null) 'title': title,
      if (eventAt != null) 'event_at': eventAt,
      if (eventTimeZone != null) 'event_time_zone': eventTimeZone,
      if (location != null) 'location': location,
      if (description != null) 'description': description,
      if (themeKind != null) 'theme_kind': themeKind,
      if (presetKey != null) 'preset_key': presetKey,
      if (mediaAssetId != null) 'media_asset_id': mediaAssetId,
      if (overlayKey != null) 'overlay_key': overlayKey,
      if (role != null) 'role': role,
      if (memberCount != null) 'member_count': memberCount,
      if (revision != null) 'revision': revision,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (serverNow != null) 'server_now': serverNow,
      if (syncedAt != null) 'synced_at': syncedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  CachedRoomsCompanion copyWith({
    Value<String>? accountId,
    Value<String>? roomId,
    Value<String>? title,
    Value<DateTime>? eventAt,
    Value<String>? eventTimeZone,
    Value<String?>? location,
    Value<String?>? description,
    Value<String>? themeKind,
    Value<String>? presetKey,
    Value<String?>? mediaAssetId,
    Value<String>? overlayKey,
    Value<String>? role,
    Value<int>? memberCount,
    Value<int>? revision,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
    Value<DateTime>? serverNow,
    Value<DateTime>? syncedAt,
    Value<int>? rowid,
  }) {
    return CachedRoomsCompanion(
      accountId: accountId ?? this.accountId,
      roomId: roomId ?? this.roomId,
      title: title ?? this.title,
      eventAt: eventAt ?? this.eventAt,
      eventTimeZone: eventTimeZone ?? this.eventTimeZone,
      location: location ?? this.location,
      description: description ?? this.description,
      themeKind: themeKind ?? this.themeKind,
      presetKey: presetKey ?? this.presetKey,
      mediaAssetId: mediaAssetId ?? this.mediaAssetId,
      overlayKey: overlayKey ?? this.overlayKey,
      role: role ?? this.role,
      memberCount: memberCount ?? this.memberCount,
      revision: revision ?? this.revision,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      serverNow: serverNow ?? this.serverNow,
      syncedAt: syncedAt ?? this.syncedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (accountId.present) {
      map['account_id'] = Variable<String>(accountId.value);
    }
    if (roomId.present) {
      map['room_id'] = Variable<String>(roomId.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (eventAt.present) {
      map['event_at'] = Variable<DateTime>(eventAt.value);
    }
    if (eventTimeZone.present) {
      map['event_time_zone'] = Variable<String>(eventTimeZone.value);
    }
    if (location.present) {
      map['location'] = Variable<String>(location.value);
    }
    if (description.present) {
      map['description'] = Variable<String>(description.value);
    }
    if (themeKind.present) {
      map['theme_kind'] = Variable<String>(themeKind.value);
    }
    if (presetKey.present) {
      map['preset_key'] = Variable<String>(presetKey.value);
    }
    if (mediaAssetId.present) {
      map['media_asset_id'] = Variable<String>(mediaAssetId.value);
    }
    if (overlayKey.present) {
      map['overlay_key'] = Variable<String>(overlayKey.value);
    }
    if (role.present) {
      map['role'] = Variable<String>(role.value);
    }
    if (memberCount.present) {
      map['member_count'] = Variable<int>(memberCount.value);
    }
    if (revision.present) {
      map['revision'] = Variable<int>(revision.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (serverNow.present) {
      map['server_now'] = Variable<DateTime>(serverNow.value);
    }
    if (syncedAt.present) {
      map['synced_at'] = Variable<DateTime>(syncedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('CachedRoomsCompanion(')
          ..write('accountId: $accountId, ')
          ..write('roomId: $roomId, ')
          ..write('title: $title, ')
          ..write('eventAt: $eventAt, ')
          ..write('eventTimeZone: $eventTimeZone, ')
          ..write('location: $location, ')
          ..write('description: $description, ')
          ..write('themeKind: $themeKind, ')
          ..write('presetKey: $presetKey, ')
          ..write('mediaAssetId: $mediaAssetId, ')
          ..write('overlayKey: $overlayKey, ')
          ..write('role: $role, ')
          ..write('memberCount: $memberCount, ')
          ..write('revision: $revision, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('serverNow: $serverNow, ')
          ..write('syncedAt: $syncedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $CacheListsTable extends CacheLists
    with TableInfo<$CacheListsTable, CacheList> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CacheListsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _accountIdMeta = const VerificationMeta(
    'accountId',
  );
  @override
  late final GeneratedColumn<String> accountId = GeneratedColumn<String>(
    'account_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _syncedAtMeta = const VerificationMeta(
    'syncedAt',
  );
  @override
  late final GeneratedColumn<DateTime> syncedAt = GeneratedColumn<DateTime>(
    'synced_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [accountId, syncedAt];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'cache_lists';
  @override
  VerificationContext validateIntegrity(
    Insertable<CacheList> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('account_id')) {
      context.handle(
        _accountIdMeta,
        accountId.isAcceptableOrUnknown(data['account_id']!, _accountIdMeta),
      );
    } else if (isInserting) {
      context.missing(_accountIdMeta);
    }
    if (data.containsKey('synced_at')) {
      context.handle(
        _syncedAtMeta,
        syncedAt.isAcceptableOrUnknown(data['synced_at']!, _syncedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_syncedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {accountId};
  @override
  CacheList map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return CacheList(
      accountId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}account_id'],
      )!,
      syncedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}synced_at'],
      )!,
    );
  }

  @override
  $CacheListsTable createAlias(String alias) {
    return $CacheListsTable(attachedDatabase, alias);
  }
}

class CacheList extends DataClass implements Insertable<CacheList> {
  final String accountId;
  final DateTime syncedAt;
  const CacheList({required this.accountId, required this.syncedAt});
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['account_id'] = Variable<String>(accountId);
    map['synced_at'] = Variable<DateTime>(syncedAt);
    return map;
  }

  CacheListsCompanion toCompanion(bool nullToAbsent) {
    return CacheListsCompanion(
      accountId: Value(accountId),
      syncedAt: Value(syncedAt),
    );
  }

  factory CacheList.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return CacheList(
      accountId: serializer.fromJson<String>(json['accountId']),
      syncedAt: serializer.fromJson<DateTime>(json['syncedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'accountId': serializer.toJson<String>(accountId),
      'syncedAt': serializer.toJson<DateTime>(syncedAt),
    };
  }

  CacheList copyWith({String? accountId, DateTime? syncedAt}) => CacheList(
    accountId: accountId ?? this.accountId,
    syncedAt: syncedAt ?? this.syncedAt,
  );
  CacheList copyWithCompanion(CacheListsCompanion data) {
    return CacheList(
      accountId: data.accountId.present ? data.accountId.value : this.accountId,
      syncedAt: data.syncedAt.present ? data.syncedAt.value : this.syncedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('CacheList(')
          ..write('accountId: $accountId, ')
          ..write('syncedAt: $syncedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(accountId, syncedAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is CacheList &&
          other.accountId == this.accountId &&
          other.syncedAt == this.syncedAt);
}

class CacheListsCompanion extends UpdateCompanion<CacheList> {
  final Value<String> accountId;
  final Value<DateTime> syncedAt;
  final Value<int> rowid;
  const CacheListsCompanion({
    this.accountId = const Value.absent(),
    this.syncedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  CacheListsCompanion.insert({
    required String accountId,
    required DateTime syncedAt,
    this.rowid = const Value.absent(),
  }) : accountId = Value(accountId),
       syncedAt = Value(syncedAt);
  static Insertable<CacheList> custom({
    Expression<String>? accountId,
    Expression<DateTime>? syncedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (accountId != null) 'account_id': accountId,
      if (syncedAt != null) 'synced_at': syncedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  CacheListsCompanion copyWith({
    Value<String>? accountId,
    Value<DateTime>? syncedAt,
    Value<int>? rowid,
  }) {
    return CacheListsCompanion(
      accountId: accountId ?? this.accountId,
      syncedAt: syncedAt ?? this.syncedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (accountId.present) {
      map['account_id'] = Variable<String>(accountId.value);
    }
    if (syncedAt.present) {
      map['synced_at'] = Variable<DateTime>(syncedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('CacheListsCompanion(')
          ..write('accountId: $accountId, ')
          ..write('syncedAt: $syncedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

abstract class _$RoomCacheDatabase extends GeneratedDatabase {
  _$RoomCacheDatabase(QueryExecutor e) : super(e);
  $RoomCacheDatabaseManager get managers => $RoomCacheDatabaseManager(this);
  late final $CachedRoomsTable cachedRooms = $CachedRoomsTable(this);
  late final $CacheListsTable cacheLists = $CacheListsTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [cachedRooms, cacheLists];
}

typedef $$CachedRoomsTableCreateCompanionBuilder =
    CachedRoomsCompanion Function({
      required String accountId,
      required String roomId,
      required String title,
      required DateTime eventAt,
      required String eventTimeZone,
      Value<String?> location,
      Value<String?> description,
      required String themeKind,
      required String presetKey,
      Value<String?> mediaAssetId,
      required String overlayKey,
      required String role,
      required int memberCount,
      required int revision,
      required DateTime createdAt,
      required DateTime updatedAt,
      required DateTime serverNow,
      required DateTime syncedAt,
      Value<int> rowid,
    });
typedef $$CachedRoomsTableUpdateCompanionBuilder =
    CachedRoomsCompanion Function({
      Value<String> accountId,
      Value<String> roomId,
      Value<String> title,
      Value<DateTime> eventAt,
      Value<String> eventTimeZone,
      Value<String?> location,
      Value<String?> description,
      Value<String> themeKind,
      Value<String> presetKey,
      Value<String?> mediaAssetId,
      Value<String> overlayKey,
      Value<String> role,
      Value<int> memberCount,
      Value<int> revision,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<DateTime> serverNow,
      Value<DateTime> syncedAt,
      Value<int> rowid,
    });

class $$CachedRoomsTableFilterComposer
    extends Composer<_$RoomCacheDatabase, $CachedRoomsTable> {
  $$CachedRoomsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get accountId => $composableBuilder(
    column: $table.accountId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get roomId => $composableBuilder(
    column: $table.roomId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get eventAt => $composableBuilder(
    column: $table.eventAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get eventTimeZone => $composableBuilder(
    column: $table.eventTimeZone,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get location => $composableBuilder(
    column: $table.location,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get themeKind => $composableBuilder(
    column: $table.themeKind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get presetKey => $composableBuilder(
    column: $table.presetKey,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get mediaAssetId => $composableBuilder(
    column: $table.mediaAssetId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get overlayKey => $composableBuilder(
    column: $table.overlayKey,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get role => $composableBuilder(
    column: $table.role,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get memberCount => $composableBuilder(
    column: $table.memberCount,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get revision => $composableBuilder(
    column: $table.revision,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get serverNow => $composableBuilder(
    column: $table.serverNow,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get syncedAt => $composableBuilder(
    column: $table.syncedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$CachedRoomsTableOrderingComposer
    extends Composer<_$RoomCacheDatabase, $CachedRoomsTable> {
  $$CachedRoomsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get accountId => $composableBuilder(
    column: $table.accountId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get roomId => $composableBuilder(
    column: $table.roomId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get eventAt => $composableBuilder(
    column: $table.eventAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get eventTimeZone => $composableBuilder(
    column: $table.eventTimeZone,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get location => $composableBuilder(
    column: $table.location,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get themeKind => $composableBuilder(
    column: $table.themeKind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get presetKey => $composableBuilder(
    column: $table.presetKey,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get mediaAssetId => $composableBuilder(
    column: $table.mediaAssetId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get overlayKey => $composableBuilder(
    column: $table.overlayKey,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get role => $composableBuilder(
    column: $table.role,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get memberCount => $composableBuilder(
    column: $table.memberCount,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get revision => $composableBuilder(
    column: $table.revision,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get serverNow => $composableBuilder(
    column: $table.serverNow,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get syncedAt => $composableBuilder(
    column: $table.syncedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$CachedRoomsTableAnnotationComposer
    extends Composer<_$RoomCacheDatabase, $CachedRoomsTable> {
  $$CachedRoomsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get accountId =>
      $composableBuilder(column: $table.accountId, builder: (column) => column);

  GeneratedColumn<String> get roomId =>
      $composableBuilder(column: $table.roomId, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<DateTime> get eventAt =>
      $composableBuilder(column: $table.eventAt, builder: (column) => column);

  GeneratedColumn<String> get eventTimeZone => $composableBuilder(
    column: $table.eventTimeZone,
    builder: (column) => column,
  );

  GeneratedColumn<String> get location =>
      $composableBuilder(column: $table.location, builder: (column) => column);

  GeneratedColumn<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => column,
  );

  GeneratedColumn<String> get themeKind =>
      $composableBuilder(column: $table.themeKind, builder: (column) => column);

  GeneratedColumn<String> get presetKey =>
      $composableBuilder(column: $table.presetKey, builder: (column) => column);

  GeneratedColumn<String> get mediaAssetId => $composableBuilder(
    column: $table.mediaAssetId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get overlayKey => $composableBuilder(
    column: $table.overlayKey,
    builder: (column) => column,
  );

  GeneratedColumn<String> get role =>
      $composableBuilder(column: $table.role, builder: (column) => column);

  GeneratedColumn<int> get memberCount => $composableBuilder(
    column: $table.memberCount,
    builder: (column) => column,
  );

  GeneratedColumn<int> get revision =>
      $composableBuilder(column: $table.revision, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  GeneratedColumn<DateTime> get serverNow =>
      $composableBuilder(column: $table.serverNow, builder: (column) => column);

  GeneratedColumn<DateTime> get syncedAt =>
      $composableBuilder(column: $table.syncedAt, builder: (column) => column);
}

class $$CachedRoomsTableTableManager
    extends
        RootTableManager<
          _$RoomCacheDatabase,
          $CachedRoomsTable,
          CachedRoom,
          $$CachedRoomsTableFilterComposer,
          $$CachedRoomsTableOrderingComposer,
          $$CachedRoomsTableAnnotationComposer,
          $$CachedRoomsTableCreateCompanionBuilder,
          $$CachedRoomsTableUpdateCompanionBuilder,
          (
            CachedRoom,
            BaseReferences<_$RoomCacheDatabase, $CachedRoomsTable, CachedRoom>,
          ),
          CachedRoom,
          PrefetchHooks Function()
        > {
  $$CachedRoomsTableTableManager(
    _$RoomCacheDatabase db,
    $CachedRoomsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$CachedRoomsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$CachedRoomsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$CachedRoomsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> accountId = const Value.absent(),
                Value<String> roomId = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<DateTime> eventAt = const Value.absent(),
                Value<String> eventTimeZone = const Value.absent(),
                Value<String?> location = const Value.absent(),
                Value<String?> description = const Value.absent(),
                Value<String> themeKind = const Value.absent(),
                Value<String> presetKey = const Value.absent(),
                Value<String?> mediaAssetId = const Value.absent(),
                Value<String> overlayKey = const Value.absent(),
                Value<String> role = const Value.absent(),
                Value<int> memberCount = const Value.absent(),
                Value<int> revision = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<DateTime> serverNow = const Value.absent(),
                Value<DateTime> syncedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => CachedRoomsCompanion(
                accountId: accountId,
                roomId: roomId,
                title: title,
                eventAt: eventAt,
                eventTimeZone: eventTimeZone,
                location: location,
                description: description,
                themeKind: themeKind,
                presetKey: presetKey,
                mediaAssetId: mediaAssetId,
                overlayKey: overlayKey,
                role: role,
                memberCount: memberCount,
                revision: revision,
                createdAt: createdAt,
                updatedAt: updatedAt,
                serverNow: serverNow,
                syncedAt: syncedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String accountId,
                required String roomId,
                required String title,
                required DateTime eventAt,
                required String eventTimeZone,
                Value<String?> location = const Value.absent(),
                Value<String?> description = const Value.absent(),
                required String themeKind,
                required String presetKey,
                Value<String?> mediaAssetId = const Value.absent(),
                required String overlayKey,
                required String role,
                required int memberCount,
                required int revision,
                required DateTime createdAt,
                required DateTime updatedAt,
                required DateTime serverNow,
                required DateTime syncedAt,
                Value<int> rowid = const Value.absent(),
              }) => CachedRoomsCompanion.insert(
                accountId: accountId,
                roomId: roomId,
                title: title,
                eventAt: eventAt,
                eventTimeZone: eventTimeZone,
                location: location,
                description: description,
                themeKind: themeKind,
                presetKey: presetKey,
                mediaAssetId: mediaAssetId,
                overlayKey: overlayKey,
                role: role,
                memberCount: memberCount,
                revision: revision,
                createdAt: createdAt,
                updatedAt: updatedAt,
                serverNow: serverNow,
                syncedAt: syncedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$CachedRoomsTableProcessedTableManager =
    ProcessedTableManager<
      _$RoomCacheDatabase,
      $CachedRoomsTable,
      CachedRoom,
      $$CachedRoomsTableFilterComposer,
      $$CachedRoomsTableOrderingComposer,
      $$CachedRoomsTableAnnotationComposer,
      $$CachedRoomsTableCreateCompanionBuilder,
      $$CachedRoomsTableUpdateCompanionBuilder,
      (
        CachedRoom,
        BaseReferences<_$RoomCacheDatabase, $CachedRoomsTable, CachedRoom>,
      ),
      CachedRoom,
      PrefetchHooks Function()
    >;
typedef $$CacheListsTableCreateCompanionBuilder =
    CacheListsCompanion Function({
      required String accountId,
      required DateTime syncedAt,
      Value<int> rowid,
    });
typedef $$CacheListsTableUpdateCompanionBuilder =
    CacheListsCompanion Function({
      Value<String> accountId,
      Value<DateTime> syncedAt,
      Value<int> rowid,
    });

class $$CacheListsTableFilterComposer
    extends Composer<_$RoomCacheDatabase, $CacheListsTable> {
  $$CacheListsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get accountId => $composableBuilder(
    column: $table.accountId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get syncedAt => $composableBuilder(
    column: $table.syncedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$CacheListsTableOrderingComposer
    extends Composer<_$RoomCacheDatabase, $CacheListsTable> {
  $$CacheListsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get accountId => $composableBuilder(
    column: $table.accountId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get syncedAt => $composableBuilder(
    column: $table.syncedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$CacheListsTableAnnotationComposer
    extends Composer<_$RoomCacheDatabase, $CacheListsTable> {
  $$CacheListsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get accountId =>
      $composableBuilder(column: $table.accountId, builder: (column) => column);

  GeneratedColumn<DateTime> get syncedAt =>
      $composableBuilder(column: $table.syncedAt, builder: (column) => column);
}

class $$CacheListsTableTableManager
    extends
        RootTableManager<
          _$RoomCacheDatabase,
          $CacheListsTable,
          CacheList,
          $$CacheListsTableFilterComposer,
          $$CacheListsTableOrderingComposer,
          $$CacheListsTableAnnotationComposer,
          $$CacheListsTableCreateCompanionBuilder,
          $$CacheListsTableUpdateCompanionBuilder,
          (
            CacheList,
            BaseReferences<_$RoomCacheDatabase, $CacheListsTable, CacheList>,
          ),
          CacheList,
          PrefetchHooks Function()
        > {
  $$CacheListsTableTableManager(_$RoomCacheDatabase db, $CacheListsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$CacheListsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$CacheListsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$CacheListsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> accountId = const Value.absent(),
                Value<DateTime> syncedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => CacheListsCompanion(
                accountId: accountId,
                syncedAt: syncedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String accountId,
                required DateTime syncedAt,
                Value<int> rowid = const Value.absent(),
              }) => CacheListsCompanion.insert(
                accountId: accountId,
                syncedAt: syncedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$CacheListsTableProcessedTableManager =
    ProcessedTableManager<
      _$RoomCacheDatabase,
      $CacheListsTable,
      CacheList,
      $$CacheListsTableFilterComposer,
      $$CacheListsTableOrderingComposer,
      $$CacheListsTableAnnotationComposer,
      $$CacheListsTableCreateCompanionBuilder,
      $$CacheListsTableUpdateCompanionBuilder,
      (
        CacheList,
        BaseReferences<_$RoomCacheDatabase, $CacheListsTable, CacheList>,
      ),
      CacheList,
      PrefetchHooks Function()
    >;

class $RoomCacheDatabaseManager {
  final _$RoomCacheDatabase _db;
  $RoomCacheDatabaseManager(this._db);
  $$CachedRoomsTableTableManager get cachedRooms =>
      $$CachedRoomsTableTableManager(_db, _db.cachedRooms);
  $$CacheListsTableTableManager get cacheLists =>
      $$CacheListsTableTableManager(_db, _db.cacheLists);
}
