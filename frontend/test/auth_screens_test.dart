import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';
import 'package:hyped/features/authentication/presentation/screens/sign_in_screen.dart';
import 'package:hyped/features/authentication/presentation/screens/splash_screen.dart';
import 'package:hyped/features/home/presentation/screens/home_screen.dart';
import 'package:mocktail/mocktail.dart';

class MockScreenRepository extends Mock implements AuthRepository {}

void main() {
  testWidgets('splash displays brand and loading state', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: SplashScreen()));
    expect(find.text('Hyped!'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('sign-in displays Google and Apple actions', (tester) async {
    final controller = AuthController(MockScreenRepository());
    await tester.pumpWidget(
      ProviderScope(
        overrides: [authControllerProvider.overrideWith((ref) => controller)],
        child: const MaterialApp(home: SignInScreen()),
      ),
    );
    expect(find.text('Continue with Google'), findsOneWidget);
    expect(find.text('Continue with Apple'), findsOneWidget);
  });

  testWidgets('authenticated home displays empty state and logout', (
    tester,
  ) async {
    final repository = MockScreenRepository();
    when(repository.hasSession).thenAnswer((_) async => true);
    final controller = AuthController(repository);
    await controller.initialize();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [authControllerProvider.overrideWith((ref) => controller)],
        child: const MaterialApp(home: HomeScreen()),
      ),
    );
    expect(find.text('Your countdowns will live here'), findsOneWidget);
    expect(find.byTooltip('Sign out'), findsOneWidget);
  });
}
