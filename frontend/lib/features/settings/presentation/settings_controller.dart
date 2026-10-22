import 'package:flutter/foundation.dart';
import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';
import 'package:hyped/features/settings/data/account_api.dart';
import 'package:hyped/features/settings/data/recent_authentication.dart';

class SettingsController extends ChangeNotifier {
  SettingsController(this.api, this.auth, this.recent, this.tokens);
  final AccountApi api;
  final AuthController auth;
  final RecentAuthentication recent;
  final TokenStore tokens;
  AccountProfile? profile;
  List<ActiveDevice> devices = const [];
  DeletionReadiness? readiness;
  bool loading = false;
  bool busy = false;
  String? error;
  bool _disposed = false;
  bool get _active => !_disposed && auth.state.isAuthenticated;
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  Future<void> load() async {
    if (!_active || loading || busy) return;
    loading = true;
    error = null;
    _notify();
    try {
      final session = (await tokens.read())?.sessionId;
      final value = await api.profile();
      final sessions = await api.devices();
      if (!_active || (await tokens.read())?.sessionId != session) return;
      if (!sessions.any((device) => device.isCurrent)) {
        await auth.clearLocalSession(expectedSessionId: session);
        return;
      }
      profile = value;
      devices = sessions;
    } catch (failure) {
      if (_active) error = accountError(failure);
    } finally {
      loading = false;
      _notify();
    }
  }

  Future<bool> revoke(ActiveDevice device) => _perform(() async {
    final session = (await tokens.read())?.sessionId;
    if (session == null) return;
    await api.revoke(device, session);
    if (!_active || (await tokens.read())?.sessionId != session) return;
    if (device.isCurrent) {
      await auth.clearLocalSession(expectedSessionId: session);
    } else {
      final sessions = await api.devices();
      if (_active) devices = sessions;
    }
  });
  Future<bool> checkReadiness() => _perform(() async {
    final value = await api.readiness();
    if (_active) readiness = value;
  });
  Future<bool> delete(String confirmation) async {
    if (confirmation != 'DELETE' || readiness?.ready != true) return false;
    return _perform(() async {
      final session = await tokens.read();
      if (session == null) {
        throw const AccountApiException('SESSION_EXPIRED', 401);
      }
      final proof = await recent.token();
      if (!_active || (await tokens.read())?.sessionId != session.sessionId) {
        throw const AccountApiException('SESSION_CHANGED');
      }
      try {
        await api.delete(proof, session.sessionId);
      } on AccountApiException catch (failure) {
        if (failure.code != 'ACCOUNT_DELETION_PENDING' &&
            failure.code != 'ACCOUNT_DELETED') {
          rethrow;
        }
      }
      // Backend acceptance is irreversible, including when external cleanup is queued for retry.
      profile = null;
      devices = const [];
      readiness = null;
      await auth.clearLocalSession(expectedSessionId: session.sessionId);
    });
  }

  Future<bool> logout() => _perform(() async {
    await auth.logout();
  });
  Future<bool> _perform(Future<void> Function() operation) async {
    if (!_active || busy || loading) return false;
    busy = true;
    error = null;
    _notify();
    try {
      await operation();
      return true;
    } catch (failure) {
      if (_active) error = accountError(failure);
      return false;
    } finally {
      busy = false;
      _notify();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    profile = null;
    devices = const [];
    readiness = null;
    super.dispose();
  }
}

String accountError(Object error) => error is AccountApiException
    ? switch (error.code) {
        'OWNERSHIP_TRANSFER_REQUIRED' =>
          'Transfer ownership or permanently delete every owned room first, including archived rooms.',
        'ACCOUNT_DELETION_UNAVAILABLE' =>
          'Account deletion is temporarily unavailable. Please try again later.',
        'ROOMS_MUST_BE_LEFT' =>
          'Leave all joined rooms before deleting your account.',
        'RECENT_AUTHENTICATION_REQUIRED' =>
          'Confirm with Google again to delete your account.',
        _ => 'Could not update your account. Check your connection and retry.',
      }
    : 'Could not update your account. Please retry or confirm with Google again.';
