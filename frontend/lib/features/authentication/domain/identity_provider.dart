enum SignInProvider { google }

abstract interface class IdentityProvider {
  Future<String> firebaseIdToken();
  Future<void> signOut();
}

class IdentitySelectionCancelledException implements Exception {
  const IdentitySelectionCancelledException();
}

class IdentityTokenMissingException implements Exception {
  const IdentityTokenMissingException();
}

class IdentityProviderException implements Exception {
  const IdentityProviderException();
}

class FirebaseConfigurationException implements Exception {
  const FirebaseConfigurationException();
}
