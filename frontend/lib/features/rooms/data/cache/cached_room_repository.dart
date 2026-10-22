import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/data/room_repository.dart';
import 'package:hyped/features/rooms/data/cache/room_cache_database.dart';
import 'package:hyped/features/rooms/data/cache/room_cache_session.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_commands.dart';
import 'package:hyped/features/rooms/domain/room_lifecycle.dart';

abstract interface class OfflineRoomReads {
  Future<CachedRoomList?> cachedList();
  Future<Room?> cachedDetail(String id);
  DateTime? get lastListSync;
}

class CachedRoomRepository extends RoomRepository implements OfflineRoomReads {
  CachedRoomRepository(
    this.api,
    this.cache,
    this.tokens, {
    DateTime Function()? clock,
  }) : clock = clock ?? (() => DateTime.now().toUtc()),
       super(api);
  final RoomApi api;
  final RoomCacheSession cache;
  final TokenStore tokens;
  final DateTime Function() clock;
  @override
  DateTime? lastListSync;
  Future<CacheIdentity?> _restored() async {
    final session = await tokens.read();
    if (session == null) return null;
    try {
      await cache.restore(session.sessionId);
      final identity = cache.current;
      final latest = await tokens.read();
      return latest?.sessionId == session.sessionId &&
              identity != null &&
              cache.matches(identity)
          ? identity
          : null;
    } catch (_) {
      return null;
    }
  }

  Future<CacheIdentity> _identity() async {
    final restored = await _restored();
    if (restored != null) return restored;
    if (cache.blocked) throw const RoomApiException(code: 'SESSION_CHANGED');
    final epoch = cache.epoch;
    final session = await tokens.read();
    if (session == null) {
      throw const RoomApiException(code: 'SESSION_EXPIRED', statusCode: 401);
    }
    // Upgrade path for credentials created before account-bound cache metadata existed.
    final account = await api.accountId(session.sessionId);
    if ((await tokens.read())?.sessionId != session.sessionId) {
      throw const RoomApiException(code: 'SESSION_CHANGED');
    }
    await cache.bind(account, session.sessionId, epoch);
    final identity = cache.current;
    if (identity == null) throw const RoomApiException(code: 'SESSION_CHANGED');
    return identity;
  }

  Future<void> _requireCurrent(CacheIdentity identity) async {
    final session = await tokens.read();
    if (!cache.matches(identity) || session?.sessionId != identity.sessionId) {
      throw const RoomApiException(code: 'SESSION_CHANGED');
    }
  }

  void _checkRead(int generation) {
    if (cache.readGeneration != generation) {
      throw const RoomApiException(code: 'CACHE_CHANGED');
    }
  }

  @override
  Future<CachedRoomList?> cachedList() async {
    final identity = await _restored();
    if (identity == null) return null;
    try {
      return await cache.run<CachedRoomList?>(
        identity,
        () => cache.database.readList(identity.accountId),
      );
    } catch (_) {
      return null;
    } // Cache failure never prevents authoritative network reads.
  }

  @override
  Future<Room?> cachedDetail(String id) async {
    final identity = await _restored();
    if (identity == null) return null;
    try {
      return await cache.run<Room?>(
        identity,
        () => cache.database.readRoom(identity.accountId, id),
      );
    } catch (_) {
      return null;
    }
  }

  @override
  Future<List<Room>> listRooms() async {
    final identity = await _identity();
    var generation = cache.readGeneration;
    final List<Room> rooms;
    try {
      rooms = await api.listRooms(limit: 50, sessionId: identity.sessionId);
    } on RoomApiException catch (failure) {
      if ([401, 403].contains(failure.statusCode)) {
        cache.invalidateReads(identity);
        try {
          await cache.run(identity, cache.database.erase);
        } catch (_) {}
      }
      rethrow;
    }
    await _requireCurrent(identity);
    _checkRead(generation);
    // Replacing the known working set also fences older detail reads for evicted/archived rooms.
    cache.invalidateReads(identity);
    generation = cache.readGeneration;
    final synced = clock().toUtc();
    final stamped = rooms
        .map((room) => room.copyWith(isCached: false, lastSyncedAt: synced))
        .toList();
    try {
      await cache.run(identity, () async {
        _checkRead(generation);
        await cache.database.replaceList(identity.accountId, stamped, synced);
      });
    } catch (
      _
    ) {} // Live data remains usable if storage is full/corrupt/unavailable.
    await _requireCurrent(identity);
    _checkRead(generation);
    lastListSync = synced;
    return stamped;
  }

  @override
  Future<Room> getRoom(String roomId) async {
    final identity = await _identity();
    final generation = cache.readGeneration;
    try {
      final room = await api.getRoom(roomId, identity.sessionId);
      return await _store(identity, room, generation: generation);
    } on RoomApiException catch (failure) {
      if ([403, 404, 410].contains(failure.statusCode)) {
        cache.invalidateReads(identity);
        try {
          await cache.run(
            identity,
            () => cache.database.remove(identity.accountId, roomId),
          );
        } catch (_) {}
      }
      rethrow;
    }
  }

  Future<Room> _store(
    CacheIdentity identity,
    Room room, {
    int? generation,
    bool mutation = false,
  }) async {
    await _requireCurrent(identity);
    if (generation != null) _checkRead(generation);
    if (mutation || room.status != RoomStatus.active) {
      cache.invalidateReads(identity);
    }
    final writeGeneration = cache.readGeneration;
    final synced = clock().toUtc();
    final stamped = room.copyWith(isCached: false, lastSyncedAt: synced);
    try {
      await cache.run(identity, () async {
        _checkRead(writeGeneration);
        await cache.database.put(identity.accountId, stamped, synced);
      });
    } catch (_) {}
    await _requireCurrent(identity);
    _checkRead(writeGeneration);
    return stamped;
  }

  @override
  Future<Room> createRoom(RoomDraft draft) async {
    final identity = await _identity();
    return _store(identity, await super.createRoom(draft), mutation: true);
  }

  @override
  Future<Room> updateRoom(Room room, RoomDraft draft) async {
    if (room.isCached) throw const RoomApiException(code: 'OFFLINE_READ_ONLY');
    final identity = await _identity();
    return _store(
      identity,
      await super.updateRoom(room, draft),
      mutation: true,
    );
  }

  @override
  Future<Room?> lifecycle(Room room, RoomLifecycleAction action) async {
    if (room.isCached) throw const RoomApiException(code: 'OFFLINE_READ_ONLY');
    final identity = await _identity();
    final updated = await super.lifecycle(room, action);
    if (updated != null) return _store(identity, updated, mutation: true);
    await _requireCurrent(identity);
    cache.invalidateReads(identity);
    try {
      await cache.run(
        identity,
        () => cache.database.remove(identity.accountId, room.id),
      );
    } catch (_) {}
    return null;
  }

  @override
  Future<RoomEventTheme> updateEventTheme(Room room, RoomDraft draft) async {
    if (room.isCached) throw const RoomApiException(code: 'OFFLINE_READ_ONLY');
    final identity = await _identity();
    final updated = await super.updateEventTheme(room, draft);
    await _requireCurrent(identity);
    cache.invalidateReads(identity);
    // This narrower response omits authorization/member fields. Invalidate rather than merge a private API snapshot.
    try {
      await cache.run(
        identity,
        () => cache.database.remove(identity.accountId, room.id),
      );
    } catch (_) {}
    return updated;
  }
}
