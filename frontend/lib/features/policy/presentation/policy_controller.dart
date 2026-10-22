import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';
import 'package:hyped/features/policy/data/policy_api.dart';

enum PolicyPhase { signedOut, loading, required, accepted, failure }

class PolicyController extends ChangeNotifier {
  PolicyController(this.api, this.auth, this.tokens) {
    auth.addListener(_authChanged);
    _authChanged();
  }
  final PolicyApi api;
  final AuthController auth;
  final TokenStore tokens;
  PolicyPhase phase = PolicyPhase.signedOut;
  PolicyStatus? status;
  bool saving = false;
  String? error;
  bool _authenticated = false;
  bool _disposed = false;
  int _generation = 0;
  bool get accepted => phase == PolicyPhase.accepted;
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  void _authChanged() {
    final authenticated = auth.state.isAuthenticated;
    if (authenticated == _authenticated) return;
    _authenticated = authenticated;
    _generation++;
    status = null;
    error = null;
    saving = false;
    phase = authenticated ? PolicyPhase.loading : PolicyPhase.signedOut;
    if (authenticated) {
      unawaited(load());
    } else {
      _notify();
    }
  }

  Future<void> load() async {
    if (!_authenticated || _disposed || saving) return;
    final generation = ++_generation;
    final acceptedBefore = accepted;
    if (!acceptedBefore) phase = PolicyPhase.loading;
    error = null;
    _notify();
    try {
      final session = (await tokens.read())?.sessionId;
      if (session == null) throw const PolicyApiException('SESSION_EXPIRED');
      final value = await api.status(session);
      final latestSession = (await tokens.read())?.sessionId;
      if (_disposed || generation != _generation || latestSession != session) {
        return;
      }
      status = value;
      phase = value.accepted && value.available
          ? PolicyPhase.accepted
          : PolicyPhase.required;
    } catch (_) {
      if (_disposed || generation != _generation) return;
      phase = acceptedBefore ? PolicyPhase.accepted : PolicyPhase.failure;
      error =
          'Could not check your legal choices. Check your connection and retry.';
    }
    _notify();
  }

  void requireReview() {
    if (!_authenticated || _disposed) return;
    phase = PolicyPhase.required;
    status = null;
    _notify();
    unawaited(load());
  }

  Future<bool> accept({
    required bool terms,
    required bool privacy,
    required bool adult,
  }) async {
    final current = status;
    if (!_authenticated ||
        _disposed ||
        saving ||
        phase != PolicyPhase.required ||
        current?.available != true ||
        !terms ||
        !privacy ||
        !adult) {
      return false;
    }
    final generation = _generation;
    saving = true;
    error = null;
    _notify();
    try {
      final session = (await tokens.read())?.sessionId;
      if (session == null) throw const PolicyApiException('SESSION_EXPIRED');
      final value = await api.accept(current!.required, session);
      final latestSession = (await tokens.read())?.sessionId;
      if (_disposed || generation != _generation || latestSession != session) {
        return false;
      }
      if (!value.accepted || !value.available) {
        throw const PolicyApiException('EXPLICIT_CONSENT_REQUIRED');
      }
      status = value;
      phase = PolicyPhase.accepted;
      return true;
    } on PolicyApiException catch (failure) {
      if (!_disposed && generation == _generation) {
        if (failure.code == 'POLICY_VERSION_CHANGED') {
          saving = false;
          await load();
          error =
              'The documents changed. Read the current versions and confirm each choice again.';
        } else {
          error = 'Your acceptance could not be confirmed. Please retry.';
        }
      }
      return false;
    } catch (_) {
      if (!_disposed && generation == _generation) {
        error = 'Your acceptance could not be confirmed. Please retry.';
      }
      return false;
    } finally {
      if (generation == _generation) saving = false;
      _notify();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    auth.removeListener(_authChanged);
    status = null;
    super.dispose();
  }
}
