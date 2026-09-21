import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:hyped/core/auth/auth_tokens.dart';
import 'package:uuid/uuid.dart';

abstract interface class TokenStore {
  Future<AuthTokens?> read();
  Future<void> write(AuthTokens tokens);
  Future<void> clear();
  Future<String> installationId();
}

class SecureTokenStore implements TokenStore {
  SecureTokenStore(this._storage);
  static const _access = 'auth.access_token';
  static const _refresh = 'auth.refresh_token';
  static const _accessExpiry = 'auth.access_expiry';
  static const _refreshExpiry = 'auth.refresh_expiry';
  static const _session = 'auth.session_id';
  static const _installation = 'app.installation_id';
  final FlutterSecureStorage _storage;

  @override
  Future<AuthTokens?> read() async {
    final values = await _storage.readAll();
    final requiredValues = [
      _access,
      _refresh,
      _accessExpiry,
      _refreshExpiry,
      _session,
    ].map((key) => values[key]).toList();
    if (requiredValues.any((value) => value == null)) return null;
    return AuthTokens(
      accessToken: values[_access]!,
      refreshToken: values[_refresh]!,
      accessTokenExpiresAt: DateTime.parse(values[_accessExpiry]!),
      refreshTokenExpiresAt: DateTime.parse(values[_refreshExpiry]!),
      sessionId: values[_session]!,
    );
  }

  @override
  Future<void> write(AuthTokens tokens) => Future.wait([
    _storage.write(key: _access, value: tokens.accessToken),
    _storage.write(key: _refresh, value: tokens.refreshToken),
    _storage.write(
      key: _accessExpiry,
      value: tokens.accessTokenExpiresAt.toIso8601String(),
    ),
    _storage.write(
      key: _refreshExpiry,
      value: tokens.refreshTokenExpiresAt.toIso8601String(),
    ),
    _storage.write(key: _session, value: tokens.sessionId),
  ]);

  @override
  Future<void> clear() => Future.wait([
    _storage.delete(key: _access),
    _storage.delete(key: _refresh),
    _storage.delete(key: _accessExpiry),
    _storage.delete(key: _refreshExpiry),
    _storage.delete(key: _session),
  ]);

  @override
  Future<String> installationId() async {
    final existing = await _storage.read(key: _installation);
    if (existing != null) return existing;
    final created = const Uuid().v4();
    await _storage.write(key: _installation, value: created);
    return created;
  }
}
