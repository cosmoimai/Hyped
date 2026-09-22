enum SignInProvider { google, apple }

abstract interface class IdentityProvider {
  Future<String> firebaseIdToken();
  Future<void> signOut();
}
