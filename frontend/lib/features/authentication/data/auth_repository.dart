import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/authentication/data/auth_api.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';

class AuthRepository {
  AuthRepository(this._api, this._tokens);
  final AuthApi _api;
  final TokenStore _tokens;
  Future<SessionRestoreResult> restoreSession(DateTime now) async {
    final stored = await _tokens.read();
    if (stored == null) return SessionRestoreResult.missing;
    if (stored.accessTokenExpiresAt.isAfter(now)) {
      return SessionRestoreResult.authenticated;
    }
    if (!stored.refreshTokenExpiresAt.isAfter(now)) {
      await _tokens.clear();
      return SessionRestoreResult.refreshFailed;
    }
    try {
      final refreshed = await _api.refresh(
        stored.refreshToken,
        await _tokens.installationId(),
      );
      await _tokens.write(refreshed);
      return SessionRestoreResult.authenticated;
    } catch (_) {
      await _tokens.clear();
      return SessionRestoreResult.refreshFailed;
    }
  }

  Future<void> signIn(IdentityProvider provider) async {
    final firebaseToken = await provider.firebaseIdToken();
    final exchange = await _api.exchange(
      firebaseIdToken: firebaseToken,
      installationId: await _tokens.installationId(),
      deviceName: 'Hyped mobile device',
      appVersion: '0.1.0+1',
    );
    await _tokens.write(exchange.tokens);
  }

  Future<void> logout() async {
    try {
      if (await _tokens.read() != null) await _api.logout();
    } finally {
      await _tokens.clear();
    }
  }
}

enum SessionRestoreResult { missing, authenticated, refreshFailed }
