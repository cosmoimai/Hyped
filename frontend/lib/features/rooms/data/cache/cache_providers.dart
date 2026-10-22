import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:hyped/core/offline/offline_state.dart';
import 'package:hyped/features/rooms/data/cache/room_cache_database.dart';
import 'package:hyped/features/rooms/data/cache/room_cache_session.dart';

final roomCacheDatabaseProvider = Provider<RoomCacheDatabase>((ref) {
  final database = RoomCacheDatabase.device();
  ref.onDispose(() => unawaited(database.close()));
  return database;
});
final roomCacheSessionProvider = Provider<RoomCacheSession>(
  (ref) => RoomCacheSession(
    ref.read(roomCacheDatabaseProvider),
    SecureCacheIdentityStore(const FlutterSecureStorage()),
  ),
);
final offlineStateProvider = ChangeNotifierProvider<OfflineState>(
  (ref) => OfflineState(),
);
