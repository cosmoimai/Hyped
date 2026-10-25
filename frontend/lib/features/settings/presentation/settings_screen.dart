import 'package:hyped/features/rooms/domain/room.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/authentication/data/firebase_identity_providers.dart';
import 'package:hyped/features/settings/data/account_api.dart';
import 'package:hyped/features/settings/data/recent_authentication.dart';
import 'package:hyped/features/settings/data/theme_preference.dart';
import 'package:hyped/features/settings/presentation/settings_controller.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_formatters.dart';

final accountApiProvider = Provider<AccountApi>(
  (ref) => AccountApi(ref.read(authenticatedDioProvider)),
);
final recentAuthenticationProvider = Provider<RecentAuthentication>(
  (ref) => GoogleRecentAuthentication(
    DefaultFirebaseBootstrap(),
    ref.read(googleAccountSelectorProvider),
  ),
);
final settingsControllerProvider =
    ChangeNotifierProvider.autoDispose<SettingsController>((ref) {
      ref.watch(authControllerProvider.select((auth) => auth.state.status));
      final controller = SettingsController(
        ref.read(accountApiProvider),
        ref.read(authControllerProvider),
        ref.read(recentAuthenticationProvider),
        ref.read(tokenStoreProvider),
      );
      controller.load();
      return controller;
    });

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});
  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && mounted) {
      ref.read(settingsControllerProvider).load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = ref.watch(settingsControllerProvider);
    final theme = ref.watch(themeControllerProvider);
    final profile = controller.profile;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Profile and Settings'),
        leading: IconButton(
          tooltip: 'Back to rooms',
          onPressed: () => context.go('/home'),
          icon: const Icon(Icons.arrow_back),
        ),
      ),
      body: RefreshIndicator(
        onRefresh: controller.load,
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            if (controller.loading || controller.busy)
              const LinearProgressIndicator(),
            if (profile != null) ...[
              CircleAvatar(
                radius: 30,
                child: Text(
                  profile.displayName.trim().isEmpty
                      ? '?'
                      : profile.displayName
                            .trim()
                            .characters
                            .first
                            .toUpperCase(),
                ),
              ),
              const SizedBox(height: 12),
              Text(
                profile.displayName,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 8),
              Text(
                '${profile.activeDevices}/5 active devices · ${profile.ownedRooms}/10 owned rooms · ${profile.joinedRooms}/25 joined rooms',
                textAlign: TextAlign.center,
              ),
            ],
            if (controller.error case final error?) ...[
              Text(error),
              TextButton(
                onPressed: controller.busy || controller.loading
                    ? null
                    : controller.load,
                child: const Text('Retry'),
              ),
            ],
            const SizedBox(height: 24),
            DropdownButtonFormField<ThemeMode>(
              initialValue: theme.mode,
              key: ValueKey(theme.mode),
              decoration: const InputDecoration(labelText: 'App theme'),
              items: const [
                DropdownMenuItem(
                  value: ThemeMode.system,
                  child: Text('System'),
                ),
                DropdownMenuItem(value: ThemeMode.light, child: Text('Light')),
                DropdownMenuItem(value: ThemeMode.dark, child: Text('Dark')),
              ],
              onChanged: theme.saving
                  ? null
                  : (value) {
                      if (value != null) theme.set(value);
                    },
            ),
            if (theme.error case final error?) Text(error),
            ListTile(
              title: const Text('Notifications'),
              subtitle: const Text('Android permission and event reminders'),
              trailing: const Icon(Icons.notifications_outlined),
              onTap: () => context.push('/notifications'),
            ),
            ListTile(
              title: const Text('Per-room reminders'),
              subtitle: const Text(
                'Choose a room to change its three fixed reminders',
              ),
              onTap: () => context.push('/settings/rooms'),
            ),
            ListTile(
              title: const Text('Terms of Service'),
              leading: const Icon(Icons.description_outlined),
              onTap: () => context.push('/legal/terms'),
            ),
            ListTile(
              title: const Text('Privacy Policy'),
              leading: const Icon(Icons.privacy_tip_outlined),
              onTap: () => context.push('/legal/privacy'),
            ),
            ListTile(
              title: const Text('Open-source licenses'),
              leading: const Icon(Icons.info_outline),
              onTap: () =>
                  showLicensePage(context: context, applicationName: 'Hyped!'),
            ),
            const Divider(),
            Text(
              'Active devices',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            for (final device in controller.devices)
              ListTile(
                title: Text(device.name),
                subtitle: Text(
                  '${device.isCurrent ? 'This device · ' : ''}${device.platform} · Last active ${compactDateTime(device.lastUsedAt)}',
                ),
                trailing: IconButton(
                  tooltip: device.isCurrent
                      ? 'Sign out this device'
                      : 'Revoke device',
                  icon: const Icon(Icons.logout),
                  onPressed: controller.busy || controller.loading
                      ? null
                      : () async {
                          final confirmed = await _confirm(
                            context,
                            device.isCurrent
                                ? 'Sign out this device?'
                                : 'Revoke device?',
                            device.isCurrent
                                ? 'This will clear your credentials and private app data on this phone.'
                                : 'This device will lose access. Its user will need to sign in again.',
                          );
                          if (confirmed && context.mounted) {
                            await controller.revoke(device);
                          }
                        },
                ),
              ),
            const Divider(),
            OutlinedButton(
              onPressed: controller.busy
                  ? null
                  : () async {
                      if (await _confirm(
                            context,
                            'Sign out?',
                            'Private room data, notifications, widget content and pending invites will be cleared from this phone.',
                          ) &&
                          context.mounted) {
                        await controller.logout();
                      }
                    },
              child: const Text('Sign out'),
            ),
            TextButton(
              onPressed: controller.busy
                  ? null
                  : () async {
                      if (!await controller.checkReadiness() ||
                          !context.mounted) {
                        return;
                      }
                      final readiness = controller.readiness;
                      if (readiness == null) return;
                      if (!readiness.ready) {
                        await showDialog<void>(
                          context: context,
                          builder: (context) => AlertDialog(
                            title: const Text(
                              'Leave or transfer your rooms first',
                            ),
                            content: SingleChildScrollView(
                              child: Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  const Text(
                                    'Transfer ownership or permanently delete all owned rooms, including archived rooms, and leave every joined room before deleting your account.',
                                  ),
                                  for (final room in readiness.ownedRooms)
                                    Text(room.title),
                                  Text(
                                    '${readiness.joinedRoomCount} joined rooms remain.',
                                  ),
                                ],
                              ),
                            ),
                            actions: [
                              TextButton(
                                onPressed: () => Navigator.pop(context),
                                child: const Text('Close'),
                              ),
                              TextButton(
                                onPressed: () {
                                  Navigator.pop(context);
                                  context.go('/home');
                                },
                                child: const Text('Open rooms'),
                              ),
                            ],
                          ),
                        );
                        return;
                      }
                      final confirmation = await showDialog<String>(
                        context: context,
                        builder: (_) => const _DeleteConfirmation(),
                      );
                      if (confirmation == 'DELETE' && context.mounted) {
                        await controller.delete(confirmation!);
                      }
                    },
              child: const Text('Permanently delete account'),
            ),
          ],
        ),
      ),
    );
  }
}

Future<bool> _confirm(
  BuildContext context,
  String title,
  String impact,
) async =>
    await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(impact),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Confirm'),
          ),
        ],
      ),
    ) ??
    false;

class _DeleteConfirmation extends StatefulWidget {
  const _DeleteConfirmation();
  @override
  State<_DeleteConfirmation> createState() => _DeleteConfirmationState();
}

class _DeleteConfirmationState extends State<_DeleteConfirmation> {
  final text = TextEditingController();
  @override
  void dispose() {
    text.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Permanently delete account?'),
    content: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text(
            'This is irreversible and has no recovery period. Access ends immediately when accepted. Your Firebase sign-in account and personal data will be deleted; cleanup completes within 24 hours. Encrypted backups expire within seven days and their personal-data key is destroyed. Google itself is not deleted. Confirm with Google, then this phone will be signed out.',
          ),
          const SizedBox(height: 16),
          TextField(
            controller: text,
            onChanged: (_) => setState(() {}),
            decoration: const InputDecoration(
              labelText: 'Type DELETE to continue',
            ),
            autocorrect: false,
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancel'),
      ),
      FilledButton(
        onPressed: text.text == 'DELETE'
            ? () => Navigator.pop(context, 'DELETE')
            : null,
        child: const Text('Confirm with Google and delete'),
      ),
    ],
  );
}

class RoomNotificationChoicesScreen extends ConsumerWidget {
  const RoomNotificationChoicesScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rooms = ref.watch(roomListControllerProvider);
    final activeRooms = rooms.rooms
        .where((room) => room.status == RoomStatus.active)
        .toList();
    return Scaffold(
      appBar: AppBar(title: const Text('Per-room reminders')),
      body: rooms.loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              children: [
                if (rooms.error case final error?)
                  ListTile(
                    title: Text(error),
                    trailing: TextButton(
                      onPressed: rooms.load,
                      child: const Text('Retry'),
                    ),
                  ),
                for (final room in activeRooms)
                  ListTile(
                    title: Text(room.title),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => context.push('/rooms/${room.id}/reminders'),
                  ),
                if (activeRooms.isEmpty && rooms.error == null)
                  const ListTile(title: Text('No active rooms.')),
              ],
            ),
    );
  }
}
