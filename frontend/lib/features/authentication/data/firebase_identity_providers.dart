import 'package:firebase_auth/firebase_auth.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';

abstract class FirebaseIdentityProvider implements IdentityProvider {
  FirebaseIdentityProvider(this._auth);
  final FirebaseAuth _auth;
  AuthProvider get provider;
  @override
  Future<String> firebaseIdToken() async {
    final credential = await _auth.signInWithProvider(provider);
    final token = await credential.user?.getIdToken(true);
    if (token == null || token.isEmpty) {
      throw StateError('Firebase did not issue an identity token');
    }
    return token;
  }

  @override
  Future<void> signOut() => _auth.signOut();
}

class GoogleIdentityProvider extends FirebaseIdentityProvider {
  GoogleIdentityProvider(super.auth);
  @override
  AuthProvider get provider => GoogleAuthProvider();
}

class AppleIdentityProvider extends FirebaseIdentityProvider {
  AppleIdentityProvider(super.auth);
  @override
  AuthProvider get provider => AppleAuthProvider();
}
