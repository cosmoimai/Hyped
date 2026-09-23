import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/features/authentication/data/firebase_identity_providers.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';

class FakeBootstrap implements FirebaseBootstrap {
  FakeBootstrap({this.error});
  final Object? error;
  var calls = 0;

  @override
  Future<void> initialize() async {
    calls++;
    if (error case final error?) throw error;
  }
}

class FakeAccounts implements GoogleAccountSelector {
  FakeAccounts({this.token = 'google-id-token', this.error});
  final String token;
  final Object? error;
  var signOutCalls = 0;

  @override
  Future<String> selectIdToken() async {
    if (error case final error?) throw error;
    return token;
  }

  @override
  Future<void> signOut() async => signOutCalls++;
}

class FakeFirebaseAuth implements FirebaseCredentialAuthenticator {
  FakeFirebaseAuth({this.token = 'fresh-firebase-token', this.error});
  final String token;
  final Object? error;
  String? receivedGoogleToken;
  var signOutCalls = 0;

  @override
  Future<String> authenticateWithGoogle(String googleIdToken) async {
    receivedGoogleToken = googleIdToken;
    if (error case final error?) throw error;
    return token;
  }

  @override
  Future<void> signOut() async => signOutCalls++;
}

void main() {
  test(
    'selects Google account and returns a fresh Firebase ID token',
    () async {
      final bootstrap = FakeBootstrap();
      final accounts = FakeAccounts();
      final firebase = FakeFirebaseAuth();
      final provider = GoogleIdentityProvider(bootstrap, accounts, firebase);

      expect(await provider.firebaseIdToken(), 'fresh-firebase-token');
      expect(firebase.receivedGoogleToken, 'google-id-token');
      expect(bootstrap.calls, 1);
    },
  );

  test('propagates account selection cancellation', () async {
    final provider = GoogleIdentityProvider(
      FakeBootstrap(),
      FakeAccounts(error: const IdentitySelectionCancelledException()),
      FakeFirebaseAuth(),
    );

    await expectLater(
      provider.firebaseIdToken(),
      throwsA(isA<IdentitySelectionCancelledException>()),
    );
  });

  test('propagates Firebase authentication failure', () async {
    final provider = GoogleIdentityProvider(
      FakeBootstrap(),
      FakeAccounts(),
      FakeFirebaseAuth(error: const IdentityProviderException()),
    );

    await expectLater(
      provider.firebaseIdToken(),
      throwsA(isA<IdentityProviderException>()),
    );
  });

  test('propagates missing Firebase ID token', () async {
    final provider = GoogleIdentityProvider(
      FakeBootstrap(),
      FakeAccounts(),
      FakeFirebaseAuth(error: const IdentityTokenMissingException()),
    );

    await expectLater(
      provider.firebaseIdToken(),
      throwsA(isA<IdentityTokenMissingException>()),
    );
  });

  test('configuration failure is explicit and safe', () async {
    final provider = GoogleIdentityProvider(
      FakeBootstrap(error: const FirebaseConfigurationException()),
      FakeAccounts(),
      FakeFirebaseAuth(),
    );

    await expectLater(
      provider.firebaseIdToken(),
      throwsA(isA<FirebaseConfigurationException>()),
    );
  });

  test('logout clears Google and Firebase sessions', () async {
    final accounts = FakeAccounts();
    final firebase = FakeFirebaseAuth();
    final provider = GoogleIdentityProvider(
      FakeBootstrap(),
      accounts,
      firebase,
    );

    await provider.signOut();

    expect(accounts.signOutCalls, 1);
    expect(firebase.signOutCalls, 1);
  });
}
