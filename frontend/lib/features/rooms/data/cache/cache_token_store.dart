import 'package:hyped/core/auth/auth_tokens.dart';
import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/rooms/data/cache/room_cache_session.dart';

class CacheTokenStore implements TokenStore {
  CacheTokenStore(this.inner, this.cache);
  final TokenStore inner;
  final RoomCacheSession cache;
  @override
  Future<AuthTokens?> read() => inner.read();
  @override
  Future<void> write(AuthTokens tokens) async {
    final previous = await inner.read();
    if (previous != null && previous.sessionId != tokens.sessionId) {
      try {
        await cache.clear();
      } catch (_) {}
    }
    await inner.write(tokens);
  }

  @override
  Future<String> installationId() => inner.installationId();
  @override
  Future<void> clear() async {
    try {
      await cache.clear();
    } finally {
      await inner.clear();
    }
  }
}
