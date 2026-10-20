import 'package:hyped/core/auth/auth_tokens.dart';
import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/notifications/data/notification_platform.dart';

class NotificationTokenStore implements TokenStore {
  NotificationTokenStore(this.inner, this.platform);
  final TokenStore inner;
  final NotificationPlatform platform;
  @override
  Future<AuthTokens?> read() => inner.read();
  @override
  Future<void> write(AuthTokens tokens) => inner.write(tokens);
  @override
  Future<String> installationId() => inner.installationId();
  @override
  Future<void> clear() async {
    try {
      await platform.clearSession();
      try {
        await platform.deleteToken();
      } catch (_) {}
    } finally {
      await inner.clear();
    }
  }
}
