import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';

abstract interface class FirebaseBootstrap {
  Future<void> initialize();
}

class DefaultFirebaseBootstrap implements FirebaseBootstrap {
  Future<void>? _initialization;

  @override
  Future<void> initialize() => _initialization ??= _initialize();

  Future<void> _initialize() async {
    try {
      if (Firebase.apps.isEmpty) await Firebase.initializeApp();
    } catch (_) {
      _initialization = null;
      throw const FirebaseConfigurationException();
    }
  }
}

abstract interface class GoogleAccountSelector {
  Future<String> selectIdToken();
  Future<void> signOut();
}

class DefaultGoogleAccountSelector implements GoogleAccountSelector {
  DefaultGoogleAccountSelector(this._googleSignIn);

  final GoogleSignIn _googleSignIn;
  Future<void>? _initialization;

  Future<void> _initialize() =>
      _initialization ??= _googleSignIn.initialize().catchError((Object error) {
        _initialization = null;
        throw error;
      });

  @override
  Future<String> selectIdToken() async {
    try {
      await _initialize();
      final account = await _googleSignIn.authenticate();
      final token = account.authentication.idToken;
      if (token == null || token.isEmpty) {
        throw const IdentityTokenMissingException();
      }
      return token;
    } on IdentityTokenMissingException {
      rethrow;
    } on GoogleSignInException catch (error) {
      if (error.code == GoogleSignInExceptionCode.canceled) {
        throw const IdentitySelectionCancelledException();
      }
      if (error.code == GoogleSignInExceptionCode.clientConfigurationError ||
          error.code == GoogleSignInExceptionCode.providerConfigurationError) {
        throw const FirebaseConfigurationException();
      }
      throw const IdentityProviderException();
    } catch (_) {
      throw const IdentityProviderException();
    }
  }

  @override
  Future<void> signOut() async {
    await _initialize();
    await _googleSignIn.signOut();
  }
}

abstract interface class FirebaseCredentialAuthenticator {
  Future<String> authenticateWithGoogle(String googleIdToken);
  Future<void> signOut();
}

class DefaultFirebaseCredentialAuthenticator
    implements FirebaseCredentialAuthenticator {
  @override
  Future<String> authenticateWithGoogle(String googleIdToken) async {
    try {
      final credential = GoogleAuthProvider.credential(idToken: googleIdToken);
      final result = await FirebaseAuth.instance.signInWithCredential(
        credential,
      );
      final token = await result.user?.getIdToken(true);
      if (token == null || token.isEmpty) {
        throw const IdentityTokenMissingException();
      }
      return token;
    } on IdentityTokenMissingException {
      rethrow;
    } on FirebaseAuthException {
      throw const IdentityProviderException();
    }
  }

  @override
  Future<void> signOut() => FirebaseAuth.instance.signOut();
}

class GoogleIdentityProvider implements IdentityProvider {
  GoogleIdentityProvider(this._bootstrap, this._accounts, this._firebaseAuth);

  final FirebaseBootstrap _bootstrap;
  final GoogleAccountSelector _accounts;
  final FirebaseCredentialAuthenticator _firebaseAuth;

  @override
  Future<String> firebaseIdToken() async {
    await _bootstrap.initialize();
    final googleIdToken = await _accounts.selectIdToken();
    return _firebaseAuth.authenticateWithGoogle(googleIdToken);
  }

  @override
  Future<void> signOut() async {
    try {
      await _accounts.signOut();
    } finally {
      await _firebaseAuth.signOut();
    }
  }
}
