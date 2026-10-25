import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/features/safety/presentation/safety_providers.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/rooms/presentation/screens/member_screen.dart';
import 'package:hyped/features/media/presentation/room_photo_providers.dart';

const reportReasons = {
  'INAPPROPRIATE_CONTENT': 'Inappropriate content',
  'HARASSMENT': 'Harassment',
  'HATE_OR_VIOLENCE': 'Hate or violence',
  'SPAM': 'Spam',
  'OTHER': 'Something else',
};
Future<void> showReport(
  BuildContext context, {
  required String roomId,
  required String subject,
  String? target,
}) => showDialog<void>(
  context: context,
  builder: (_) =>
      _ReportDialog(roomId: roomId, subject: subject, target: target),
);

class _ReportDialog extends ConsumerStatefulWidget {
  const _ReportDialog({
    required this.roomId,
    required this.subject,
    this.target,
  });
  final String roomId, subject;
  final String? target;
  @override
  ConsumerState<_ReportDialog> createState() => _ReportDialogState();
}

class _ReportDialogState extends ConsumerState<_ReportDialog> {
  final details = TextEditingController();
  String? reason;
  @override
  void dispose() {
    details.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final controller = ref.watch(safetyControllerProvider);
    return PopScope(
      canPop: !controller.busy,
      child: AlertDialog(
        title: Text(
          widget.subject == 'ACCOUNT'
              ? 'Report account'
              : widget.subject == 'THEME'
              ? 'Report room photo or style'
              : 'Report room',
        ),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Choose a reason for review. Reporting does not block anyone. Do not include passwords, invitation codes, or other private information.',
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                initialValue: reason,
                decoration: const InputDecoration(labelText: 'Reason'),
                isExpanded: true,
                items: [
                  for (final entry in reportReasons.entries)
                    DropdownMenuItem(
                      value: entry.key,
                      child: Text(entry.value),
                    ),
                ],
                onChanged: controller.busy
                    ? null
                    : (value) => setState(() => reason = value),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: details,
                enabled: !controller.busy,
                maxLength: 500,
                minLines: 2,
                maxLines: 4,
                decoration: const InputDecoration(
                  labelText: 'Details (optional)',
                ),
              ),
              if (controller.error case final error?) Text(error),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: controller.busy ? null : () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: controller.busy || reason == null
                ? null
                : () async {
                    final success = await controller.submitReport(
                      widget.roomId,
                      widget.subject,
                      widget.target,
                      reason!,
                      details.text,
                    );
                    if (!context.mounted || !success) return;
                    final messenger = ScaffoldMessenger.of(context);
                    Navigator.pop(context);
                    messenger.showSnackBar(
                      const SnackBar(content: Text('Report sent for review.')),
                    );
                  },
            child: Text(controller.busy ? 'Sending…' : 'Send report'),
          ),
        ],
      ),
    );
  }
}

Future<void> showBlock(
  BuildContext context,
  WidgetRef ref, {
  required String roomId,
  required String target,
  required String displayName,
}) async {
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (_) => AlertDialog(
      title: Text('Block $displayName?'),
      content: const Text(
        'They will be removed from rooms you own, and you will leave rooms they own. In other shared rooms, your profiles will be hidden from each other. Neither account can join rooms owned by the other. They will not be notified. Unblocking will not restore memberships.',
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(context, true),
          child: const Text('Block account'),
        ),
      ],
    ),
  );
  if (confirmed != true || !context.mounted) return;
  // Keep the controller alive across the asynchronous mutation.
  final subscription = ref.listenManual(safetyControllerProvider, (_, _) {});
  try {
    final controller = ref.read(safetyControllerProvider);
    if (await controller.block(target, roomId) && context.mounted) {
      ref.invalidate(memberControllerProvider(roomId));
      ref.invalidate(roomDetailControllerProvider);
      ref.invalidate(roomPhotoBytesProvider);
      ref.invalidate(blockedAccountsProvider);
      ref.invalidate(roomListControllerProvider);
      context.go('/home');
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Account blocked. Refreshing your rooms.'),
        ),
      );
    } else if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(controller.error ?? 'Could not block this account.'),
        ),
      );
    }
  } finally {
    subscription.close();
  }
}
