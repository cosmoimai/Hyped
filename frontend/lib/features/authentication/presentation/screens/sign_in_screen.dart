import 'package:hyped/core/presentation/countdown_mark.dart';
import 'package:go_router/go_router.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';

class SignInScreen extends ConsumerWidget {
  const SignInScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.watch(authControllerProvider);
    final loading = controller.state.status == AuthStatus.authenticating;
    Future<void> signIn(SignInProvider kind) async {
      try {
        final provider = await ref.read(identityProviderFactoryProvider)(kind);
        await controller.signIn(provider);
      } catch (_) {
        controller.reportSignInFailure();
      }
    }

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Center(child: CountdownMark(size: 72)),
                  const SizedBox(height: 24),
                  Text(
                    'Welcome to Hyped!',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'Sign in to create and join private countdowns.',
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 32),
                  Container(
                    padding: const EdgeInsets.all(28),
                    decoration: BoxDecoration(
                      color: Theme.of(context).colorScheme.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(24),
                    ),
                    child: Column(
                      children: [
                        const Icon(Icons.auto_awesome_outlined, size: 36),
                        const SizedBox(height: 16),
                        Text(
                          'Count down together.',
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.headlineMedium,
                        ),
                        const SizedBox(height: 12),
                        const Text(
                          'Trips, birthdays, and the little things. Give your next moment a place to belong.',
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  ),
                  if (controller.state.message case final message?) ...[
                    const SizedBox(height: 20),
                    Text(
                      message,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                      textAlign: TextAlign.center,
                    ),
                    for (final device in controller.state.recoveryDevices)
                      ListTile(
                        leading: const Icon(Icons.devices),
                        title: Text(device.deviceName),
                        subtitle: Text(device.platform.toLowerCase()),
                      ),
                    TextButton(
                      onPressed: controller.retry,
                      child: const Text('Try again'),
                    ),
                  ],
                  const SizedBox(height: 28),
                  if (loading)
                    Semantics(
                      label: 'Signing in',
                      child: const CircularProgressIndicator(),
                    )
                  else ...[
                    FilledButton.icon(
                      onPressed: () => signIn(SignInProvider.google),
                      icon: const Icon(Icons.login, size: 20),
                      label: const Text('Continue with Google'),
                    ),
                  ],
                  TextButton(
                    onPressed: () => context.push('/legal/terms'),
                    child: const Text('Terms of Service'),
                  ),
                  TextButton(
                    onPressed: () => context.push('/legal/privacy'),
                    child: const Text('Privacy Policy'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
