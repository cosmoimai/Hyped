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
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                'Welcome to Hyped!',
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 12),
              const Text('Sign in to create and join private countdowns.'),
              if (controller.state.message case final message?) ...[
                const SizedBox(height: 20),
                Text(
                  message,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
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
                FilledButton(
                  onPressed: () => signIn(SignInProvider.google),
                  child: const Text('Continue with Google'),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
