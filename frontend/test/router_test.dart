import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/app/router/app_router.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';
import 'package:mocktail/mocktail.dart';

class MockRouterRepository extends Mock implements AuthRepository {}

void main() {
  testWidgets('signed-out private route redirects to sign-in', (tester) async {
    final repository = MockRouterRepository();
    when(repository.hasSession).thenAnswer((_) async => false);
    final auth = AuthController(repository);
    await auth.initialize();
    final router = createRouter(auth);
    addTearDown(router.dispose);
    router.go('/home');
    await tester.pumpWidget(
      ProviderScope(
        overrides: [authControllerProvider.overrideWith((ref) => auth)],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Welcome to Hyped!'), findsOneWidget);
  });

  testWidgets('authenticated user is redirected to home', (tester) async {
    final repository = MockRouterRepository();
    when(repository.hasSession).thenAnswer((_) async => true);
    final auth = AuthController(repository);
    await auth.initialize();
    final router = createRouter(auth);
    addTearDown(router.dispose);
    router.go('/sign-in');
    await tester.pumpWidget(
      ProviderScope(
        overrides: [authControllerProvider.overrideWith((ref) => auth)],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Your countdowns will live here'), findsOneWidget);
  });
}
