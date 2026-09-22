import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
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
}
