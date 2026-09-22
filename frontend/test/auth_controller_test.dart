import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/data/auth_api.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';
import 'package:mocktail/mocktail.dart';

class MockRepository extends Mock implements AuthRepository {}

class MockIdentityProvider extends Mock implements IdentityProvider {}

void main() {
  late MockRepository repository;
  late AuthController controller;
  setUp(() {
    repository = MockRepository();
    controller = AuthController(repository);
  });

  test('restores authenticated and signed-out states', () async {
    when(
      () => repository.restoreSession(any()),
    ).thenAnswer((_) async => SessionRestoreResult.authenticated);
    await controller.initialize();
    expect(controller.state.status, AuthStatus.authenticated);
    when(
      () => repository.restoreSession(any()),
    ).thenAnswer((_) async => SessionRestoreResult.missing);
    await controller.initialize();
    expect(controller.state.status, AuthStatus.signedOut);
  });

  test('failed startup refresh exposes a safe sign-in error', () async {
    when(
      () => repository.restoreSession(any()),
    ).thenAnswer((_) async => SessionRestoreResult.refreshFailed);
    await controller.initialize();
    expect(controller.state.status, AuthStatus.failure);
    expect(
      controller.state.message,
      'Your session expired. Please sign in again.',
    );
  });

  test('sign-in exposes loading, success and retryable failure', () async {
    final identity = MockIdentityProvider();
    when(() => repository.signIn(identity)).thenAnswer((_) async {});
    await controller.signIn(identity);
    expect(controller.state.status, AuthStatus.authenticated);
    when(
      () => repository.signIn(identity),
    ).thenThrow(StateError('private provider detail'));
    await controller.signIn(identity);
    expect(controller.state.status, AuthStatus.failure);
    expect(
      controller.state.message,
      isNot(contains('private provider detail')),
    );
    controller.retry();
    expect(controller.state.status, AuthStatus.signedOut);
  });

  test('logout always transitions to signed out', () async {
    when(repository.logout).thenThrow(StateError('network'));
    await expectLater(controller.logout(), completes);
    expect(controller.state.status, AuthStatus.signedOut);
  });

  test('account selection cancellation returns to signed out', () async {
    final identity = MockIdentityProvider();
    when(
      () => repository.signIn(identity),
    ).thenThrow(const IdentitySelectionCancelledException());

    await controller.signIn(identity);

    expect(controller.state.status, AuthStatus.signedOut);
  });

  test('Firebase configuration failure has a safe message', () async {
    final identity = MockIdentityProvider();
    when(
      () => repository.signIn(identity),
    ).thenThrow(const FirebaseConfigurationException());

    await controller.signIn(identity);

    expect(controller.state.message, contains('not configured'));
  });

  test('device limit retains only safe recovery-device fields', () async {
    final identity = MockIdentityProvider();
    final device = RecoveryDevice(
      deviceId: 'device-id',
      deviceName: 'Pixel 10',
      platform: 'ANDROID',
      lastActiveAt: DateTime.utc(2026),
    );
    when(
      () => repository.signIn(identity),
    ).thenThrow(DeviceLimitReachedException([device]));

    await controller.signIn(identity);

    expect(controller.state.message, contains('five active devices'));
    expect(controller.state.recoveryDevices.single.deviceName, 'Pixel 10');
  });
}
