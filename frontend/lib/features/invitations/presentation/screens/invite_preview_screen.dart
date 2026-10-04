import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/invitations/domain/invitation.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_controllers.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_providers.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_formatters.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_theme_view.dart';

class InvitePreviewScreen extends ConsumerWidget {
  const InvitePreviewScreen({required this.intent, super.key});

  final PendingInviteIntent intent;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final flow = ref.watch(inviteFlowControllerProvider(intent));
    final authenticated = ref
        .watch(authControllerProvider)
        .state
        .isAuthenticated;
    return Scaffold(
      appBar: AppBar(
        title: Text(
          intent.kind == InviteKind.roomCode ? 'Room code' : 'Invite',
        ),
      ),
      body: SafeArea(
        child: flow.loading && flow.preview == null
            ? const Center(child: CircularProgressIndicator())
            : flow.error != null && flow.preview == null
            ? _InviteError(message: flow.error!, onRetry: flow.loadPreview)
            : flow.preview == null
            ? _InviteError(
                message: 'Could not load this invite. Please try again.',
                onRetry: flow.loadPreview,
              )
            : _InviteContent(
                intent: intent,
                authenticated: authenticated,
                flow: flow,
              ),
      ),
    );
  }
}

class _InviteContent extends ConsumerWidget {
  const _InviteContent({
    required this.intent,
    required this.authenticated,
    required this.flow,
  });

  final PendingInviteIntent intent;
  final bool authenticated;
  final InviteFlowController flow;

  Future<void> _join(BuildContext context, WidgetRef ref) async {
    if (!authenticated) {
      ref.read(pendingInviteControllerProvider).remember(intent);
      context.go('/sign-in');
      return;
    }
    final result = await flow.join();
    if (!context.mounted || result == null) return;
    if (result.created) {
      ref.read(pendingInviteControllerProvider).clear();
      context.go('/rooms/${result.room.id}');
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final preview = flow.preview as InvitePreview;
    final result = flow.result;
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
      children: [
        RoomThemeView(
          theme: preview.event.theme,
          padding: const EdgeInsets.all(24),
          child: DefaultTextStyle(
            style: Theme.of(context).textTheme.bodyLarge!.copyWith(
              color: Colors.white,
              shadows: const [Shadow(color: Colors.black26, blurRadius: 8)],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  preview.event.title,
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    color: Colors.white,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 28),
                Text(
                  countdown(preview.event.eventAt, DateTime.now()),
                  style: Theme.of(context).textTheme.displaySmall?.copyWith(
                    color: Colors.white,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 8),
                Text(compactDateTime(preview.event.eventAt)),
              ],
            ),
          ),
        ),
        const SizedBox(height: 20),
        Card(
          child: ListTile(
            leading: const Icon(Icons.person_add_alt_1),
            title: Text('Invited by ${preview.inviter.displayName}'),
            subtitle: Text(
              '${preview.memberCount} member${preview.memberCount == 1 ? '' : 's'}',
            ),
          ),
        ),
        Card(
          child: ListTile(
            leading: const Icon(Icons.public),
            title: Text(preview.event.eventTimeZone),
            subtitle: const Text('Event timezone'),
          ),
        ),
        if (flow.message case final String message?) ...[
          const SizedBox(height: 12),
          _StatusBanner(message: message),
        ],
        if (flow.error case final String error?) ...[
          const SizedBox(height: 12),
          _StatusBanner(message: error, error: true),
        ],
        const SizedBox(height: 24),
        if (result != null && result.created == false)
          FilledButton(
            onPressed: () {
              ref.read(pendingInviteControllerProvider).clear();
              context.go('/rooms/${result.room.id}');
            },
            child: const Text('Open room'),
          )
        else
          FilledButton(
            onPressed: flow.joining ? null : () => _join(context, ref),
            child: flow.joining
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Text(authenticated ? 'Join room' : 'Sign in to join'),
          ),
        TextButton(
          onPressed: flow.loading ? null : flow.loadPreview,
          child: const Text('Refresh preview'),
        ),
      ],
    );
  }
}

class _StatusBanner extends StatelessWidget {
  const _StatusBanner({required this.message, this.error = false});

  final String message;
  final bool error;

  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: BoxDecoration(
      color: error
          ? Theme.of(context).colorScheme.errorContainer
          : Theme.of(context).colorScheme.secondaryContainer,
      borderRadius: BorderRadius.circular(16),
    ),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Text(
        message,
        style: TextStyle(
          color: error
              ? Theme.of(context).colorScheme.onErrorContainer
              : Theme.of(context).colorScheme.onSecondaryContainer,
        ),
      ),
    ),
  );
}

class _InviteError extends StatelessWidget {
  const _InviteError({required this.message, required this.onRetry});

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.link_off, size: 56),
          const SizedBox(height: 16),
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 16),
          FilledButton(onPressed: onRetry, child: const Text('Try again')),
        ],
      ),
    ),
  );
}
