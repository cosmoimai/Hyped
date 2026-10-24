import 'package:hyped/features/rooms/data/cache/room_cache_session.dart';
import 'package:hyped/features/notifications/data/notification_platform.dart';
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
    this.notifications,
    this.onLocalCleanup,
    this.roomCache,
  ]) : _identityProvider = identityProvider;
  final RoomWidgetRepository? widgets;
  final NotificationPlatform? notifications;
  final void Function()? onLocalCleanup;
  final RoomCacheSession? roomCache;
  final AuthApi _api;
  final TokenStore _tokens;
  final DeviceMetadataProvider _metadata;
  IdentityProvider? _identityProvider;
  Future<SessionRestoreResult> restoreSession(DateTime now) async {
    final stored = await _tokens.read();
    if (stored == null) {
      try {
        await roomCache?.clear();
      } catch (_) {}
      await widgets?.clearProtectedContent();
      await notifications?.clearSession();
      return SessionRestoreResult.missing;
    }
    if (stored.accessTokenExpiresAt.isAfter(now)) {
      try {
        await roomCache?.restore(stored.sessionId);
      } catch (_) {}
      await widgets?.startSession();
      await notifications?.startSession();
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
      try {
        await roomCache?.restore(refreshed.sessionId);
      } catch (_) {}
      await widgets?.startSession();
      await notifications?.startSession();
      return SessionRestoreResult.authenticated;
    } catch (_) {
      await _tokens.clear();
      return SessionRestoreResult.refreshFailed;
    }
  }

  Future<void> signIn(IdentityProvider provider) async {
    // Existing credentials must not survive an account switch or a cancelled replacement sign-in.
    if (await _tokens.read() != null) await clearLocalSession();
    await widgets?.clearProtectedContent();
    await notifications?.clearSession();
    try {
      await notifications?.deleteToken();
    } catch (_) {}
    final firebaseToken = await provider.firebaseIdToken();
    final metadata = await _metadata.load();
    final exchange = await _api.exchange(
      firebaseIdToken: firebaseToken,
      installationId: await _tokens.installationId(),
      deviceName: metadata.deviceName,
      appVersion: metadata.appVersion,
    );
    await _tokens.write(exchange.tokens);
    try {
      await roomCache?.start(exchange.user.id, exchange.tokens.sessionId);
    } catch (_) {}
    await widgets?.startSession();
    await notifications?.startSession();
    _identityProvider = provider;
  }

  Future<void> logout() async {
    try {
      try {
        await roomCache?.clear();
      } catch (_) {}
      try {
        await widgets?.clearProtectedContent();
      } catch (_) {}
      try {
        await notifications?.clearSession();
      } catch (_) {}
      if (await _tokens.read() != null) await _api.logout();
    } finally {
      await clearLocalSession();
    }
  }

  Future<bool> clearLocalSession({String? expectedSessionId}) async {
    if (expectedSessionId != null) {
      final current = await _tokens.read();
      if (current != null && current.sessionId != expectedSessionId) {
        return false;
      }
    }
    try {
      try {
        await widgets?.clearProtectedContent();
      } catch (_) {}
      try {
        await notifications?.clearSession();
      } catch (_) {}
      try {
        await _identityProvider?.signOut();
      } catch (_) {}
    } finally {
      _identityProvider = null;
      try {
        await _tokens.clear();
      } finally {
        onLocalCleanup?.call();
      }
    }
    return true;
  }
}

enum SessionRestoreResult { missing, authenticated, refreshFailed }
