import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/authentication/data/auth_api.dart';
import 'package:hyped/features/authentication/domain/device_metadata.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';

class AuthRepository {
  AuthRepository(
    this._api,
    this._tokens,
    this._metadata, [
    IdentityProvider? identityProvider,
    this.widgets,
  ]) : _identityProvider = identityProvider;
  final RoomWidgetRepository? widgets;
  final AuthApi _api;
  final TokenStore _tokens;
  final DeviceMetadataProvider _metadata;
  IdentityProvider? _identityProvider;
  Future<SessionRestoreResult> restoreSession(DateTime now) async {
    final stored = await _tokens.read();
    if (stored == null) {
      await widgets?.clearProtectedContent();
      return SessionRestoreResult.missing;
    }
    if (stored.accessTokenExpiresAt.isAfter(now)) {
      await widgets?.startSession();
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
      await widgets?.startSession();
      return SessionRestoreResult.authenticated;
    } catch (_) {
      await _tokens.clear();
      return SessionRestoreResult.refreshFailed;
    }
  }

  Future<void> signIn(IdentityProvider provider) async {
    // Clear even when switching directly between accounts, before any new requests.
    await widgets?.clearProtectedContent();
    final firebaseToken = await provider.firebaseIdToken();
    final metadata = await _metadata.load();
    final exchange = await _api.exchange(
      firebaseIdToken: firebaseToken,
      installationId: await _tokens.installationId(),
      deviceName: metadata.deviceName,
      appVersion: metadata.appVersion,
    );
    await _tokens.write(exchange.tokens);
    await widgets?.startSession();
    _identityProvider = provider;
  }

  Future<void> logout() async {
    try {
      await widgets?.clearProtectedContent();
      if (await _tokens.read() != null) await _api.logout();
    } finally {
      try {
        await _identityProvider?.signOut();
      } finally {
        _identityProvider = null;
        await _tokens.clear();
      }
    }
  }
}

enum SessionRestoreResult { missing, authenticated, refreshFailed }
