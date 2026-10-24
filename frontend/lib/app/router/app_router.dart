import 'package:hyped/core/offline/offline_state.dart';
import 'package:hyped/features/policy/presentation/policy_controller.dart';
import 'package:hyped/features/policy/presentation/policy_screen.dart';
import 'package:hyped/features/policy/presentation/legal_document_screen.dart';
import 'package:hyped/features/settings/presentation/settings_screen.dart';
import 'package:hyped/features/rooms/presentation/screens/room_lifecycle_screen.dart';
import 'package:hyped/features/rooms/presentation/screens/member_screen.dart';
import 'package:hyped/features/notifications/presentation/notification_settings_screen.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';
import 'package:hyped/features/authentication/presentation/screens/sign_in_screen.dart';
import 'package:hyped/features/android_widgets/presentation/screens/room_widget_settings_screen.dart';
import 'package:hyped/features/authentication/presentation/screens/splash_screen.dart';
import 'package:hyped/features/home/presentation/screens/home_screen.dart';
import 'package:hyped/features/invitations/domain/invitation.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_controllers.dart';
import 'package:hyped/features/invitations/presentation/screens/invite_preview_screen.dart';
import 'package:hyped/features/invitations/presentation/screens/room_code_entry_screen.dart';
import 'package:hyped/features/onboarding/presentation/screens/onboarding_screen.dart';
import 'package:hyped/features/rooms/presentation/screens/room_detail_screen.dart';
import 'package:hyped/features/rooms/presentation/screens/room_form_screen.dart';
import 'package:hyped/features/reminders/presentation/screens/reminder_settings_screen.dart';

GoRouter createRouter(
  AuthController auth, [
  PendingInviteController? pending,
  PolicyController? policy,
  OfflineState? offline,
  bool Function()? hasRestoredRoomCache,
]) {
  String? afterConsent;
  return GoRouter(
    initialLocation: '/splash',
    overridePlatformDefaultLocation: true,
    // Router errors can contain the full URI, including invite credentials.
    errorBuilder: (_, _) =>
        const Scaffold(body: Center(child: Text('This page is unavailable.'))),
    refreshListenable: Listenable.merge([auth, ?pending, ?policy, ?offline]),
    redirect: (context, state) {
      final status = auth.state.status;
      final path = state.matchedLocation;
      if (status == AuthStatus.initializing) {
        return path == '/splash' || _isInvitePath(path) || _isLegalPath(path)
            ? null
            : '/splash';
      }
      if (auth.state.isAuthenticated) {
        if (_isLegalPath(path) || path.startsWith('/settings')) return null;
        if (policy != null && !policy.accepted) {
          final restoredRead =
              hasRestoredRoomCache?.call() == true &&
              {PolicyPhase.loading, PolicyPhase.failure}.contains(policy.phase);
          if (restoredRead && _isOfflineReadPath(path)) return null;
          if (restoredRead &&
              pending?.intent == null &&
              (_isAuthEntryPath(path) || path == '/policy-acceptance')) {
            return '/home';
          }
          if (offline?.offline == true && policy.phase == PolicyPhase.failure) {
            if (_isOfflineReadPath(path)) return null;
            if (path == '/policy-acceptance') {
              return pending?.intent?.location ??
                  (_isOfflineReadPath(afterConsent ?? '')
                      ? afterConsent
                      : '/home');
            }
          }
          if (_isInvitePath(path)) return null;
          if (path != '/policy-acceptance' && !_isAuthEntryPath(path)) {
            afterConsent = state.uri.toString();
          }
          return path == '/policy-acceptance' ? null : '/policy-acceptance';
        }
        if (path == '/policy-acceptance') {
          final destination =
              pending?.intent?.location ?? afterConsent ?? '/home';
          afterConsent = null;
          return destination;
        }
        final pendingLocation = pending?.intent?.location;
        if (pendingLocation != null && _isAuthEntryPath(path)) {
          return pendingLocation;
        }
        return _isPrivatePath(path) || _isInvitePath(path) ? null : '/home';
      }
      if (status == AuthStatus.failure && path == '/splash') return '/sign-in';
      if (path == '/splash') return '/onboarding';
      if (_isPrivatePath(path) || path == '/policy-acceptance') {
        return '/sign-in';
      }
      return null;
    },
    routes: [
      GoRoute(
        path: '/policy-acceptance',
        builder: (_, _) => const PolicyScreen(),
      ),
      GoRoute(
        path: '/legal/:kind',
        builder: (_, state) => LegalDocumentScreen(
          kind: state.pathParameters['kind']!,
          version: state.uri.queryParameters['version'],
        ),
      ),
      GoRoute(path: '/splash', builder: (_, _) => const SplashScreen()),
      GoRoute(path: '/onboarding', builder: (_, _) => const OnboardingScreen()),
      GoRoute(path: '/sign-in', builder: (_, _) => const SignInScreen()),
      GoRoute(
        path: '/notifications',
        builder: (_, _) => const NotificationSettingsScreen(),
      ),
      GoRoute(path: '/settings', builder: (_, _) => const SettingsScreen()),
      GoRoute(
        path: '/settings/rooms',
        builder: (_, _) => const RoomNotificationChoicesScreen(),
      ),
      GoRoute(path: '/home', builder: (_, _) => const HomeScreen()),
      GoRoute(
        path: '/widgets/room',
        builder: (_, state) => RoomWidgetSettingsScreen(
          appWidgetId: int.tryParse(
            state.uri.queryParameters['widgetId'] ?? '',
          ),
        ),
      ),
      GoRoute(path: '/join', builder: (_, _) => const RoomCodeEntryScreen()),
      GoRoute(
        path: '/join/room-code/:code',
        builder: (_, state) => InvitePreviewScreen(
          intent: PendingInviteIntent(
            kind: InviteKind.roomCode,
            credential: state.pathParameters['code']!,
          ),
        ),
      ),
      GoRoute(
        path: '/invite/:token',
        builder: (_, state) => InvitePreviewScreen(
          intent: PendingInviteIntent(
            kind: InviteKind.link,
            credential: state.pathParameters['token']!,
          ),
        ),
      ),
      GoRoute(path: '/rooms/new', builder: (_, _) => const CreateRoomScreen()),
      GoRoute(
        path: '/rooms/:roomId',
        builder: (_, state) =>
            RoomDetailScreen(roomId: state.pathParameters['roomId']!),
      ),
      GoRoute(
        path: '/rooms/:roomId/edit',
        builder: (_, state) =>
            EditRoomScreen(roomId: state.pathParameters['roomId']!),
      ),
      GoRoute(
        path: '/rooms/:roomId/lifecycle',
        builder: (_, state) =>
            RoomLifecycleScreen(roomId: state.pathParameters['roomId']!),
      ),
      GoRoute(
        path: '/rooms/:roomId/members',
        builder: (_, state) =>
            MemberScreen(roomId: state.pathParameters['roomId']!),
      ),
      GoRoute(
        path: '/rooms/:roomId/reminders',
        builder: (_, state) =>
            ReminderSettingsScreen(roomId: state.pathParameters['roomId']!),
      ),
    ],
  );
}

bool _isLegalPath(String path) => path.startsWith('/legal/');

bool _isPrivatePath(String path) =>
    path.startsWith('/settings') ||
    path == '/notifications' ||
    path == '/home' ||
    path.startsWith('/rooms') ||
    path.startsWith('/widgets');

bool _isInvitePath(String path) =>
    path == '/join' || path.startsWith('/join/') || path.startsWith('/invite/');

bool _isAuthEntryPath(String path) =>
    path == '/sign-in' || path == '/onboarding' || path == '/splash';

bool _isOfflineReadPath(String path) =>
    path == '/home' ||
    (RegExp(r'^/rooms/[^/]+$').hasMatch(path) && path != '/rooms/new');
