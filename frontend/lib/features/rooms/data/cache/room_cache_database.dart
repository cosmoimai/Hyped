import 'dart:io';
import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:hyped/features/rooms/domain/room.dart';

part 'room_cache_database.g.dart';

class CachedRooms extends Table {
  TextColumn get accountId => text()();
  TextColumn get roomId => text()();
  TextColumn get title => text()();
  DateTimeColumn get eventAt => dateTime()();
  TextColumn get eventTimeZone => text()();
  TextColumn get location => text().nullable()();
  TextColumn get description => text().nullable()();
  TextColumn get themeKind => text()();
  TextColumn get presetKey => text()();
  TextColumn get mediaAssetId => text().nullable()();
  TextColumn get overlayKey => text()();
  TextColumn get role => text()();
  IntColumn get memberCount => integer()();
  IntColumn get revision => integer()();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get updatedAt => dateTime()();
  DateTimeColumn get serverNow => dateTime()();
  DateTimeColumn get syncedAt => dateTime()();
  @override
  Set<Column> get primaryKey => {accountId, roomId};
}

class CacheLists extends Table {
  TextColumn get accountId => text()();
  DateTimeColumn get syncedAt => dateTime()();
  @override
  Set<Column> get primaryKey => {accountId};
}

class CachedRoomList {
  const CachedRoomList(this.rooms, this.syncedAt);
  final List<Room> rooms;
  final DateTime syncedAt;
}

@DriftDatabase(tables: [CachedRooms, CacheLists])
class RoomCacheDatabase extends _$RoomCacheDatabase {
  RoomCacheDatabase(super.executor);
  factory RoomCacheDatabase.device() => RoomCacheDatabase(
    LazyDatabase(() async {
      final directory = await getApplicationSupportDirectory();
      return NativeDatabase.createInBackground(
        File(p.join(directory.path, 'hyped_room_cache.sqlite')),
        setup: (db) {
          db.execute('PRAGMA secure_delete = ON');
          db.execute('PRAGMA journal_mode = DELETE');
        },
      );
    }),
  );
  @override
  int get schemaVersion => 2;
  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (m) => m.createAll(),
    onUpgrade: (m, from, to) async {
      // Rebuild this derived, read-only cache on a schema mismatch. There are no offline writes to lose.
      await m.deleteTable('cached_rooms');
      await m.deleteTable('cache_lists');
      await m.createAll();
    },
    beforeOpen: (_) async {
      await customStatement('PRAGMA secure_delete = ON');
    },
  );
  Future<void> erase() => transaction(() async {
    await delete(cachedRooms).go();
    await delete(cacheLists).go();
  });
  Future<void> remove(String account, String id) =>
      (delete(cachedRooms)..where(
            (row) => row.accountId.equals(account) & row.roomId.equals(id),
          ))
          .go();
  Future<void> replaceList(
    String account,
    List<Room> rooms,
    DateTime syncedAt,
  ) => transaction(() async {
    await (delete(
      cachedRooms,
    )..where((row) => row.accountId.equals(account))).go();
    for (final room in rooms) {
      await put(account, room, syncedAt);
    }
    await into(cacheLists).insertOnConflictUpdate(
      CacheListsCompanion.insert(accountId: account, syncedAt: syncedAt),
    );
  });
  Future<void> put(String account, Room room, DateTime syncedAt) async {
    if (room.status != RoomStatus.active) {
      await remove(account, room.id);
      return;
    }
    _validate(room);
    await into(cachedRooms).insertOnConflictUpdate(
      CachedRoomsCompanion.insert(
        accountId: account,
        roomId: room.id,
        title: room.title,
        eventAt: room.eventAt.toUtc(),
        eventTimeZone: room.eventTimeZone,
        location: Value(room.location),
        description: Value(room.description),
        themeKind: room.theme.kind.wire,
        presetKey: room.theme.presetKey,
        overlayKey: room.theme.overlayKey,
        mediaAssetId: Value(room.theme.mediaAssetId),
        role: room.role.wire,
        memberCount: room.memberCount,
        revision: room.revision,
        createdAt: room.createdAt.toUtc(),
        updatedAt: room.updatedAt.toUtc(),
        serverNow: room.serverNow.toUtc(),
        syncedAt: syncedAt.toUtc(),
      ),
    );
  }

  Future<CachedRoomList?> readList(String account) async {
    final meta = await (select(
      cacheLists,
    )..where((row) => row.accountId.equals(account))).getSingleOrNull();
    if (meta == null) return null;
    final rows = await (select(
      cachedRooms,
    )..where((row) => row.accountId.equals(account))).get();
    final rooms = <Room>[];
    for (final row in rows) {
      final room = await _decode(row);
      if (room != null) rooms.add(room);
    }
    rooms.sort((a, b) => a.eventAt.compareTo(b.eventAt));
    return CachedRoomList(rooms, meta.syncedAt.toUtc());
  }

  Future<Room?> readRoom(String account, String id) async {
    final row =
        await (select(cachedRooms)..where(
              (row) => row.accountId.equals(account) & row.roomId.equals(id),
            ))
            .getSingleOrNull();
    return row == null ? null : _decode(row);
  }

  Future<Room?> _decode(CachedRoom row) async {
    try {
      final room = Room(
        id: row.roomId,
        title: row.title,
        eventAt: row.eventAt.toUtc(),
        eventTimeZone: row.eventTimeZone,
        location: row.location,
        description: row.description,
        theme: RoomTheme(
          kind: RoomThemeKindX.fromWire(row.themeKind),
          presetKey: row.presetKey,
          overlayKey: row.overlayKey,
          mediaAssetId: row.mediaAssetId,
        ),
        status: RoomStatus.active,
        role: RoomRoleX.fromWire(row.role),
        memberCount: row.memberCount,
        revision: row.revision,
        createdAt: row.createdAt.toUtc(),
        updatedAt: row.updatedAt.toUtc(),
        serverNow: row.serverNow.toUtc(),
        isCached: true,
        lastSyncedAt: row.syncedAt.toUtc(),
      );
      _validate(room);
      return room;
    } catch (_) {
      await remove(row.accountId, row.roomId);
      return null;
    }
  }

  static void _validate(Room room) {
    const presets = {
      'soft-blue-01',
      'mint-sky-01',
      'blue-lilac-02',
      'sunset-coral-01',
      'peach-gold-01',
      'aurora-green-01',
    };
    if (room.id.isEmpty ||
        room.title.trim().isEmpty ||
        room.title.runes.length > 80 ||
        room.eventTimeZone.isEmpty ||
        room.eventTimeZone.length > 80 ||
        (room.location?.runes.length ?? 0) > 120 ||
        (room.description?.runes.length ?? 0) > 500 ||
        !presets.contains(room.theme.presetKey) ||
        !{
          'dark-soft',
          'light-soft',
          'none',
          'image-dark-40',
        }.contains(room.theme.overlayKey) ||
        (room.theme.kind == RoomThemeKind.uploadedImage &&
            (room.theme.mediaAssetId == null ||
                !RegExp(
                  r'^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$',
                ).hasMatch(room.theme.mediaAssetId!))) ||
        room.memberCount < 1 ||
        room.memberCount > 25 ||
        room.revision < 1) {
      throw const FormatException('Invalid cached room projection.');
    }
  }
}
