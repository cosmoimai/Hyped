import 'package:dio/dio.dart';
import 'package:flutter/painting.dart';
import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/rooms/data/cache/room_cache_session.dart';
import 'package:hyped/features/rooms/data/room_api.dart';

/// Acknowledges durable access changes only after local protected projections are cleared.
class SafetyInvalidations {
  SafetyInvalidations(this.dio, this.cache, this.tokens, this.widgets);
  final Dio dio;
  final RoomCacheSession cache;
  final TokenStore tokens;
  final RoomWidgetRepository widgets;
  Future<void> synchronize(CacheIdentity identity) async {
    final epoch = widgets.sessionEpoch;
    bool current() => cache.matches(identity) && widgets.sessionEpoch == epoch;
    final options = Options(extra: {'expectedSessionId': identity.sessionId});
    final response = await dio.get<Map<String, dynamic>>(
      '/me/room-invalidations',
      options: options,
    );
    if (!current() || (await tokens.read())?.sessionId != identity.sessionId) {
      throw const RoomApiException(code: 'SESSION_CHANGED');
    }
    final items = response.data!['items'] as List;
    for (final item in items) {
      final room = item['roomId'] as String;
      final version = item['version'] as String;
      if (!current()) throw const RoomApiException(code: 'SESSION_CHANGED');
      bool removed = false;
      try {
        removed = await cache.removeProtected(identity, room);
      } finally {
        await widgets.markRoom(room, 'unavailable', epoch: epoch);
      }
      if (!removed) throw const RoomApiException(code: 'SESSION_CHANGED');
      PaintingBinding.instance.imageCache.clear();
      PaintingBinding.instance.imageCache.clearLiveImages();
      if (!current() ||
          (await tokens.read())?.sessionId != identity.sessionId) {
        throw const RoomApiException(code: 'SESSION_CHANGED');
      }
      try {
        await dio.delete<void>(
          '/me/room-invalidations/$version',
          options: options,
        );
      } on DioException {
        // Local removal already succeeded. Keep server work pending without blocking authoritative reads.
      }
    }
  }
}

/// A local block is already known to have changed access. Clear every derived snapshot before refresh.
Future<void> clearSafetyProjections(
  RoomCacheSession cache,
  RoomWidgetRepository widgets,
  TokenStore tokens,
  String session,
) async {
  final epoch = widgets.sessionEpoch;
  final identity = cache.current;
  if (identity != null &&
      identity.sessionId == session &&
      cache.matches(identity)) {
    try {
      await cache.removeProtected(identity);
    } catch (_) {
      /* Failed removal disables cache reads and clears persisted ownership. */
    }
  }
  try {
    if ((await tokens.read())?.sessionId != session ||
        widgets.sessionEpoch != epoch) {
      return;
    }
    final selected = await widgets.instances();
    for (final room
        in selected
            .map((instance) => instance.roomId)
            .whereType<String>()
            .toSet()) {
      if ((await tokens.read())?.sessionId != session ||
          widgets.sessionEpoch != epoch) {
        return;
      }
      await widgets.markRoom(room, 'unavailable', epoch: epoch);
    }
  } finally {
    PaintingBinding.instance.imageCache.clear();
    PaintingBinding.instance.imageCache.clearLiveImages();
  }
}
