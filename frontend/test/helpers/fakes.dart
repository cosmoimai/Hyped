import 'dart:async';
import 'package:hyped/core/api/token_refresher.dart';
import 'package:hyped/core/auth/auth_tokens.dart';
import 'package:hyped/core/secure_storage/token_store.dart';

AuthTokens tokens([String suffix = 'one']) => AuthTokens(
  accessToken: 'access-$suffix',
  refreshToken: 'refresh-$suffix',
  accessTokenExpiresAt: DateTime.utc(2026, 1, 1, 1),
  refreshTokenExpiresAt: DateTime.utc(2026, 2),
  sessionId: 'session-$suffix',
);

AuthExchangeResponse exchangeResponse([String suffix = 'one']) =>
    AuthExchangeResponse(
      tokens: tokens(suffix),
      user: const AuthUser(
        id: 'user-id',
        displayName: 'Hyped User',
        photo: null,
        profileRevision: 1,
      ),
      isNewAccount: false,
    );

class MemoryTokenStore implements TokenStore {
  MemoryTokenStore([this.value]);
  AuthTokens? value;
  var clearCount = 0;
  var writeCount = 0;
  @override
  Future<void> clear() async {
    value = null;
    clearCount++;
  }

  @override
  Future<String> installationId() async =>
      '019b1f1d-48f0-7b33-99da-4a498fe22d11';
  @override
  Future<AuthTokens?> read() async => value;
  @override
  Future<void> write(AuthTokens tokens) async {
    value = tokens;
    writeCount++;
  }
}

class FakeRefresher implements TokenRefresher {
  FakeRefresher(this.result, {this.gate});
  final AuthTokens result;
  final Completer<void>? gate;
  var calls = 0;
  @override
  Future<AuthTokens> refresh(String refreshToken, String installationId) async {
    calls++;
    await gate?.future;
    return result;
  }
}

class FailingRefresher implements TokenRefresher {
  @override
  Future<AuthTokens> refresh(String refreshToken, String installationId) =>
      throw StateError('rotation failed');
}
