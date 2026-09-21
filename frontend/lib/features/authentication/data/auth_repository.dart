import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/authentication/data/auth_api.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';

class AuthRepository {
  AuthRepository(this._api, this._tokens);
  final AuthApi _api;
  final TokenStore _tokens;
  Future<bool> hasSession() async => await _tokens.read() != null;
  Future<void> signIn(IdentityProvider provider) async {
    final firebaseToken = await provider.firebaseIdToken();
    final tokens = await _api.exchange(
      firebaseIdToken: firebaseToken,
      installationId: await _tokens.installationId(),
      deviceName: 'Hyped mobile device',
      appVersion: '0.1.0+1',
    );
    await _tokens.write(tokens);
  }

  Future<void> logout() async {
    try {
      if (await _tokens.read() != null) await _api.logout();
    } finally {
      await _tokens.clear();
    }
  }
}
