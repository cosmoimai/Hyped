import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/policy/presentation/policy_controller.dart';
import 'package:hyped/features/policy/presentation/policy_providers.dart';

class PolicyScreen extends ConsumerWidget {
  const PolicyScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.watch(policyControllerProvider);
    final status = controller.status;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Before you join the countdown'),
        automaticallyImplyLeading: false,
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            const Icon(Icons.verified_user_outlined, size: 56),
            const SizedBox(height: 20),
            Text(
              'Your choices, clearly.',
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 12),
            const Text(
              'Hyped is for adults aged 18 and over. Read the documents and confirm each choice to create or join rooms.',
            ),
            if (controller.phase == PolicyPhase.loading)
              const Padding(
                padding: EdgeInsets.all(24),
                child: Center(child: CircularProgressIndicator()),
              ),
            if (controller.error case final error?) ...[
              const SizedBox(height: 16),
              Text(error, semanticsLabel: error),
            ],
            if (status != null)
              _ConsentChoices(
                key: ValueKey(status.required.key),
                controller: controller,
              ),
            if (controller.phase == PolicyPhase.failure ||
                (status != null && !status.available)) ...[
              if (status != null && !status.available)
                const Text(
                  'These documents have not been published yet. You can read the drafts, but acceptance is unavailable.',
                ),
              TextButton(
                onPressed: controller.saving ? null : controller.load,
                child: const Text('Retry'),
              ),
            ],
            const SizedBox(height: 16),
            TextButton(
              onPressed: controller.saving
                  ? null
                  : () => context.go('/settings'),
              child: const Text('Account settings and deletion'),
            ),
            TextButton(
              onPressed: controller.saving
                  ? null
                  : ref.read(authControllerProvider).logout,
              child: const Text('Sign out'),
            ),
          ],
        ),
      ),
    );
  }
}

class _ConsentChoices extends StatefulWidget {
  const _ConsentChoices({required this.controller, super.key});
  final PolicyController controller;
  @override
  State<_ConsentChoices> createState() => _ConsentChoicesState();
}

class _ConsentChoicesState extends State<_ConsentChoices> {
  bool terms = false;
  bool privacy = false;
  bool adult = false;
  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final versions = controller.status!.required;
    final enabled =
        !controller.saving &&
        controller.status!.available &&
        controller.phase == PolicyPhase.required;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 16),
        TextButton(
          onPressed: () =>
              context.push('/legal/terms?version=${versions.terms}'),
          child: Text(
            'Read Terms of Service and Content Rules · ${versions.terms}',
          ),
        ),
        TextButton(
          onPressed: () =>
              context.push('/legal/privacy?version=${versions.privacy}'),
          child: Text('Read Privacy Policy · ${versions.privacy}'),
        ),
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          value: terms,
          onChanged: enabled
              ? (value) => setState(() => terms = value == true)
              : null,
          title: const Text('I accept the Terms of Service and Content Rules.'),
        ),
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          value: privacy,
          onChanged: enabled
              ? (value) => setState(() => privacy = value == true)
              : null,
          title: const Text('I accept the Privacy Policy.'),
        ),
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          value: adult,
          onChanged: enabled
              ? (value) => setState(() => adult = value == true)
              : null,
          title: const Text('I confirm that I am at least 18 years old.'),
        ),
        const SizedBox(height: 16),
        FilledButton(
          onPressed: enabled && terms && privacy && adult
              ? () => controller.accept(
                  terms: terms,
                  privacy: privacy,
                  adult: adult,
                )
              : null,
          child: Text(
            controller.saving ? 'Saving your choices…' : 'Accept and continue',
          ),
        ),
      ],
    );
  }
}
