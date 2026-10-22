import 'package:firebase_auth/firebase_auth.dart';
import 'package:hyped/features/authentication/data/firebase_identity_providers.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';

abstract interface class RecentAuthentication {
  Future<String> token();
}

class GoogleRecentAuthentication implements RecentAuthentication {
  GoogleRecentAuthentication(this.bootstrap, this.accounts);
  final FirebaseBootstrap bootstrap;
  final GoogleAccountSelector accounts;
  @override
  Future<String> token() async {
    await bootstrap.initialize();
    final user = FirebaseAuth.instance.currentUser;
    if (user == null) throw const IdentityProviderException();
    final googleToken = await accounts.selectIdToken();
    try {
      // Reauthenticate the existing Firebase user; selecting another account must not replace it.
      await user.reauthenticateWithCredential(
        GoogleAuthProvider.credential(idToken: googleToken),
      );
      final token = await user.getIdToken(true);
      if (token == null) throw const IdentityTokenMissingException();
      return token;
    } on FirebaseAuthException {
      throw const IdentityProviderException();
    }
  }
}
