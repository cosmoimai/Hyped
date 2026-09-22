import 'package:flutter/foundation.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/data/auth_api.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';

enum AuthStatus {
  initializing,
  signedOut,
  authenticating,
  authenticated,
  failure,
}

class AuthState {
  const AuthState(this.status, {this.message, this.recoveryDevices = const []});
  final AuthStatus status;
  final String? message;
  final List<RecoveryDevice> recoveryDevices;
  bool get isAuthenticated => status == AuthStatus.authenticated;
}

class AuthController extends ChangeNotifier {
  AuthController(this._repository, [this._invalidation])
    : _state = const AuthState(AuthStatus.initializing) {
    _invalidation?.addListener(_sessionInvalidated);
  }
  final AuthRepository _repository;
  final Listenable? _invalidation;
  AuthState _state;
  AuthState get state => _state;

  Future<void> initialize() async {
    try {
      final restored = await _repository.restoreSession(DateTime.now().toUtc());
      _set(switch (restored) {
        SessionRestoreResult.missing => const AuthState(AuthStatus.signedOut),
        SessionRestoreResult.authenticated => const AuthState(
          AuthStatus.authenticated,
        ),
        SessionRestoreResult.refreshFailed => const AuthState(
          AuthStatus.failure,
          message: 'Your session expired. Please sign in again.',
        ),
      });
    } catch (_) {
      _set(
        const AuthState(
          AuthStatus.failure,
          message: 'Unable to restore your session. Please retry.',
        ),
      );
    }
  }

  Future<void> signIn(IdentityProvider provider) async {
    _set(const AuthState(AuthStatus.authenticating));
    try {
      await _repository.signIn(provider);
      _set(const AuthState(AuthStatus.authenticated));
    } on IdentitySelectionCancelledException {
      _set(const AuthState(AuthStatus.signedOut));
    } on FirebaseConfigurationException {
      _set(
        const AuthState(
          AuthStatus.failure,
          message: 'Google sign-in is not configured for this build.',
        ),
      );
    } on DeviceLimitReachedException catch (error) {
      _set(
        AuthState(
          AuthStatus.failure,
          message: 'This account already has five active devices.',
          recoveryDevices: error.devices,
        ),
      );
    } catch (_) {
      _set(
        const AuthState(
          AuthStatus.failure,
          message: 'Sign-in could not be completed. Please try again.',
        ),
      );
    }
  }

  Future<void> logout() async {
    try {
      await _repository.logout();
    } catch (_) {
      // Local cleanup is guaranteed by the repository; remote logout is best effort.
    }
    _set(const AuthState(AuthStatus.signedOut));
  }

  void retry() => _set(const AuthState(AuthStatus.signedOut));

  void reportSignInFailure() => _set(
    const AuthState(
      AuthStatus.failure,
      message: 'Sign-in could not be completed. Please try again.',
    ),
  );

  void _set(AuthState next) {
    _state = next;
    notifyListeners();
  }

  void _sessionInvalidated() => _set(const AuthState(AuthStatus.signedOut));

  @override
  void dispose() {
    _invalidation?.removeListener(_sessionInvalidated);
    super.dispose();
  }
}

class SessionInvalidationSignal extends ChangeNotifier {
  void invalidate() => notifyListeners();
}
