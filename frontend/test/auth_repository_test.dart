import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/core/auth/auth_tokens.dart';
import 'package:hyped/features/authentication/data/auth_api.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';
import 'package:mocktail/mocktail.dart';
import 'helpers/fakes.dart';

class MockAuthApi extends Mock implements AuthApi {}

class MockProvider extends Mock implements IdentityProvider {}

void main() {
  test(
    'exchanges Firebase proof and stores only backend session tokens',
    () async {
      final api = MockAuthApi();
      final identity = MockProvider();
      final store = MemoryTokenStore();
      when(identity.firebaseIdToken).thenAnswer((_) async => 'firebase-proof');
      when(
        () => api.exchange(
          firebaseIdToken: 'firebase-proof',
          installationId: any(named: 'installationId'),
          deviceName: any(named: 'deviceName'),
          appVersion: any(named: 'appVersion'),
        ),
      ).thenAnswer((_) async => exchangeResponse());
      final repository = AuthRepository(
        api,
        store,
        FakeDeviceMetadataProvider(),
      );

      await repository.signIn(identity);

      expect(store.value?.accessToken, 'access-one');
      verify(
        () => api.exchange(
          firebaseIdToken: 'firebase-proof',
          installationId: '019b1f1d-48f0-7b33-99da-4a498fe22d11',
          deviceName: 'Pixel Test',
          appVersion: '0.1.0+1',
        ),
      ).called(1);
      verifyNever(identity.signOut);
    },
  );

  test('logout clears credentials when remote revocation fails', () async {
    final api = MockAuthApi();
    final identity = MockProvider();
    final store = MemoryTokenStore(tokens());
    when(api.logout).thenThrow(StateError('unavailable'));
    when(identity.signOut).thenAnswer((_) async {});

    await expectLater(
      AuthRepository(
        api,
        store,
        FakeDeviceMetadataProvider(),
        identity,
      ).logout(),
      throwsStateError,
    );

    expect(store.value, isNull);
    expect(store.clearCount, 1);
    verify(identity.signOut).called(1);
  });

  test('logout signs out Google and clears credentials', () async {
    final api = MockAuthApi();
    final identity = MockProvider();
    final store = MemoryTokenStore();
    when(identity.firebaseIdToken).thenAnswer((_) async => 'firebase-proof');
    when(identity.signOut).thenAnswer((_) async {});
    when(
      () => api.exchange(
        firebaseIdToken: any(named: 'firebaseIdToken'),
        installationId: any(named: 'installationId'),
        deviceName: any(named: 'deviceName'),
        appVersion: any(named: 'appVersion'),
      ),
    ).thenAnswer((_) async => exchangeResponse());
    when(api.logout).thenAnswer((_) async {});
    final repository = AuthRepository(api, store, FakeDeviceMetadataProvider());

    await repository.signIn(identity);
    await repository.logout();

    verify(identity.signOut).called(1);
    expect(store.value, isNull);
  });

  test('startup distinguishes missing and valid credentials', () async {
    final api = MockAuthApi();
    expect(
      await AuthRepository(
        api,
        MemoryTokenStore(),
        FakeDeviceMetadataProvider(),
      ).restoreSession(DateTime.utc(2026)),
      SessionRestoreResult.missing,
    );
    final valid = tokens();
    expect(
      await AuthRepository(
        api,
        MemoryTokenStore(valid),
        FakeDeviceMetadataProvider(),
      ).restoreSession(DateTime.utc(2026, 1, 1)),
      SessionRestoreResult.authenticated,
    );
    verifyNever(() => api.refresh(any(), any()));
  });

  test('startup refreshes an expired access token', () async {
    final api = MockAuthApi();
    final expired = AuthTokens(
      accessToken: 'old-access',
      refreshToken: 'old-refresh',
      accessTokenExpiresAt: DateTime.utc(2026),
      refreshTokenExpiresAt: DateTime.utc(2026, 2),
      sessionId: 'session',
    );
    final store = MemoryTokenStore(expired);
    when(
      () => api.refresh('old-refresh', any()),
    ).thenAnswer((_) async => tokens('new'));

    expect(
      await AuthRepository(
        api,
        store,
        FakeDeviceMetadataProvider(),
      ).restoreSession(DateTime.utc(2026, 1, 2)),
      SessionRestoreResult.authenticated,
    );
    expect(store.value?.accessToken, 'access-new');
  });

  test('startup refresh failure clears storage and requires sign-in', () async {
    final api = MockAuthApi();
    final expired = AuthTokens(
      accessToken: 'old-access',
      refreshToken: 'old-refresh',
      accessTokenExpiresAt: DateTime.utc(2026),
      refreshTokenExpiresAt: DateTime.utc(2026, 2),
      sessionId: 'session',
    );
    final store = MemoryTokenStore(expired);
    when(
      () => api.refresh(any(), any()),
    ).thenThrow(const AuthenticationApiException());

    expect(
      await AuthRepository(
        api,
        store,
        FakeDeviceMetadataProvider(),
      ).restoreSession(DateTime.utc(2026, 1, 2)),
      SessionRestoreResult.refreshFailed,
    );
    expect(store.value, isNull);
  });
}
