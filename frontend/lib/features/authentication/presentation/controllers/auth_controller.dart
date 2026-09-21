import 'package:flutter/foundation.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';

enum AuthStatus {
  initializing,
  signedOut,
  authenticating,
  authenticated,
  failure,
}

class AuthState {
  const AuthState(this.status, {this.message});
  final AuthStatus status;
  final String? message;
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
      _set(
        AuthState(
          await _repository.hasSession()
              ? AuthStatus.authenticated
              : AuthStatus.signedOut,
        ),
      );
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
