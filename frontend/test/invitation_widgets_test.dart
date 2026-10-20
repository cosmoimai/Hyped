import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/app/router/app_router.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';
import 'package:hyped/features/invitations/data/invitation_api.dart';
import 'package:hyped/features/invitations/data/invitation_repository.dart';
import 'package:hyped/features/invitations/domain/invitation.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_controllers.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_providers.dart';
import 'package:hyped/features/invitations/presentation/screens/room_code_entry_screen.dart';
import 'package:hyped/features/rooms/data/room_repository.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:mocktail/mocktail.dart';

class _MockAuthRepository extends Mock implements AuthRepository {}

class _MockIdentityProvider extends Mock implements IdentityProvider {}

class _MockRoomRepository extends Mock implements RoomRepository {}

void main() {
  for (final kind in InviteKind.values) {
    testWidgets(
      '$kind survives initialization, sign-in and session expiry without redirect loops',
      (tester) async {
        final repository = _MockAuthRepository();
        final restore = Completer<SessionRestoreResult>();
        when(
          () => repository.restoreSession(any()),
        ).thenAnswer((_) => restore.future);
        final identity = _MockIdentityProvider();
        when(() => repository.signIn(identity)).thenAnswer((_) async {});
        final signal = SessionInvalidationSignal();
        addTearDown(signal.dispose);
        final auth = AuthController(repository, signal);
        final initializing = auth.initialize();
        final pending = PendingInviteController();
        final intent = PendingInviteIntent(
          kind: kind,
          credential: kind == InviteKind.link ? 'opaque-token' : '7K4MP9QX',
        );
        final router = createRouter(auth, pending)..go(intent.location);
        addTearDown(router.dispose);
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              authControllerProvider.overrideWith((ref) => auth),
              pendingInviteControllerProvider.overrideWith((ref) => pending),
              invitationRepositoryProvider.overrideWith(
                (ref) => _FakeInvitationRepository(
                  previewValue: preview(),
                  joinResult: InviteJoinResult(room: room(), created: false),
                ),
              ),
            ],
            child: MaterialApp.router(routerConfig: router),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('Goa trip'), findsOneWidget);
        restore.complete(SessionRestoreResult.missing);
        await initializing;
        await tester.pumpAndSettle();
        await tester.tap(find.text('Sign in to join'));
        await tester.pumpAndSettle();
        expect(pending.intent, intent);
        expect(router.routeInformationProvider.value.uri.path, '/sign-in');
        await auth.signIn(identity);
        await tester.pumpAndSettle();
        expect(router.routeInformationProvider.value.uri.path, intent.location);
        expect(find.text('Join room'), findsOneWidget);
        await tester.tap(find.text('Join room'));
        await tester.pumpAndSettle();
        expect(find.text('Open room'), findsOneWidget);
        signal.invalidate();
        await tester.pumpAndSettle();
        expect(router.routeInformationProvider.value.uri.path, intent.location);
        expect(find.text('Sign in to join'), findsOneWidget);
        await tester.tap(find.text('Sign in to join'));
        await tester.pumpAndSettle();
        await auth.signIn(identity);
        await tester.pumpAndSettle();
        expect(router.routeInformationProvider.value.uri.path, intent.location);
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets(
    'malformed invite route does not display credentials in diagnostics',
    (tester) async {
      final auth = await _auth(SessionRestoreResult.missing);
      final router = createRouter(auth)..go('/invite/secret-token/7K4MP9QX');
      addTearDown(router.dispose);
      await tester.pumpWidget(MaterialApp.router(routerConfig: router));
      await tester.pumpAndSettle();
      expect(find.text('This page is unavailable.'), findsOneWidget);
      expect(find.textContaining('secret-token'), findsNothing);
      expect(find.textContaining('7K4MP9QX'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('room-code entry validates and routes to preview', (
    tester,
  ) async {
    final router = GoRouter(
      initialLocation: '/join',
      routes: [
        GoRoute(path: '/join', builder: (_, _) => const RoomCodeEntryScreen()),
        GoRoute(
          path: '/join/room-code/:code',
          builder: (_, state) =>
              Text('Preview ${state.pathParameters['code']}'),
        ),
      ],
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    await tester.tap(find.text('Preview room'));
    await tester.pumpAndSettle();
    expect(find.text('Enter an 8-character room code.'), findsOneWidget);

    await tester.enterText(find.byType(TextField), '7k4m-p9qx');
    await tester.tap(find.text('Preview room'));
    await tester.pumpAndSettle();

    expect(find.text('Preview 7K4MP9QX'), findsOneWidget);
  });

  testWidgets('invite routes stay public while auth initializes', (
    tester,
  ) async {
    final repository = _MockAuthRepository();
    final auth = AuthController(repository);
    final pending = PendingInviteController();
    final router = createRouter(auth, pending)..go('/invite/opaque-token');
    addTearDown(router.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authControllerProvider.overrideWith((ref) => auth),
          pendingInviteControllerProvider.overrideWith((ref) => pending),
          invitationRepositoryProvider.overrideWith(
            (ref) => _FakeInvitationRepository(previewValue: preview()),
          ),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Goa trip'), findsOneWidget);
    expect(find.text('Sign in to join'), findsOneWidget);
  });

  testWidgets(
    'signed-out invite preview preserves pending intent for sign-in',
    (tester) async {
      final auth = await _auth(SessionRestoreResult.missing);
      final pending = PendingInviteController();
      final router = createRouter(auth, pending)..go('/invite/opaque-token');
      addTearDown(router.dispose);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authControllerProvider.overrideWith((ref) => auth),
            pendingInviteControllerProvider.overrideWith((ref) => pending),
            invitationRepositoryProvider.overrideWith(
              (ref) => _FakeInvitationRepository(previewValue: preview()),
            ),
          ],
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Goa trip'), findsOneWidget);
      await tester.tap(find.text('Sign in to join'));
      await tester.pumpAndSettle();

      expect(find.text('Welcome to Hyped!'), findsOneWidget);
      expect(pending.intent, isNotNull);
      expect(pending.intent.toString(), 'PendingInviteIntent[REDACTED]');
    },
  );

  testWidgets('authenticated user returns from sign-in to pending invite', (
    tester,
  ) async {
    final auth = await _auth(SessionRestoreResult.authenticated);
    final pending = PendingInviteController()
      ..remember(
        const PendingInviteIntent(
          kind: InviteKind.link,
          credential: 'opaque-token',
        ),
      );
    final router = createRouter(auth, pending)..go('/sign-in');
    addTearDown(router.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authControllerProvider.overrideWith((ref) => auth),
          pendingInviteControllerProvider.overrideWith((ref) => pending),
          invitationRepositoryProvider.overrideWith(
            (ref) => _FakeInvitationRepository(previewValue: preview()),
          ),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Goa trip'), findsOneWidget);
    expect(find.text('Join room'), findsOneWidget);
  });

  testWidgets('successful authenticated join redirects to room detail', (
    tester,
  ) async {
    final auth = await _auth(SessionRestoreResult.authenticated);
    final pending = PendingInviteController();
    final joinedRoom = room();
    final router = createRouter(auth, pending)..go('/invite/opaque-token');
    addTearDown(router.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authControllerProvider.overrideWith((ref) => auth),
          pendingInviteControllerProvider.overrideWith((ref) => pending),
          invitationRepositoryProvider.overrideWith(
            (ref) => _FakeInvitationRepository(
              previewValue: preview(),
              joinResult: InviteJoinResult(room: joinedRoom, created: true),
            ),
          ),
          roomRepositoryProvider.overrideWith(
            (ref) => _roomRepositoryWithDetail(joinedRoom),
          ),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Join room'));
    await tester.pumpAndSettle();

    expect(find.text('2 members'), findsOneWidget);
    expect(find.byTooltip('Edit room'), findsNothing);
  });

  testWidgets('already-member join shows open-room action', (tester) async {
    final auth = await _auth(SessionRestoreResult.authenticated);
    final pending = PendingInviteController();
    final router = createRouter(auth, pending)..go('/invite/opaque-token');
    addTearDown(router.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authControllerProvider.overrideWith((ref) => auth),
          pendingInviteControllerProvider.overrideWith((ref) => pending),
          invitationRepositoryProvider.overrideWith(
            (ref) => _FakeInvitationRepository(
              previewValue: preview(),
              joinResult: InviteJoinResult(room: room(), created: false),
            ),
          ),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Join room'));
    await tester.pumpAndSettle();

    expect(find.text('You are already in this room.'), findsOneWidget);
    expect(find.text('Open room'), findsOneWidget);
  });
}

Future<AuthController> _auth(SessionRestoreResult restoreResult) async {
  final repository = _MockAuthRepository();
  when(
    () => repository.restoreSession(any()),
  ).thenAnswer((_) async => restoreResult);
  final auth = AuthController(repository);
  await auth.initialize();
  return auth;
}

class _FakeInvitationRepository extends InvitationRepository {
  _FakeInvitationRepository({required this.previewValue, this.joinResult})
    : super(InvitationApi(Dio(), Dio()));

  final InvitePreview previewValue;
  final InviteJoinResult? joinResult;

  @override
  Future<InvitePreview> preview(PendingInviteIntent intent) async =>
      previewValue;

  @override
  Future<InviteJoinResult> join(
    PendingInviteIntent intent,
    String previewReference,
  ) async {
    return joinResult ?? InviteJoinResult(room: room(), created: true);
  }
}

RoomRepository _roomRepositoryWithDetail(Room detail) {
  final repository = _MockRoomRepository();
  when(() => repository.getRoom(detail.id)).thenAnswer((_) async => detail);
  return repository;
}

InvitePreview preview() => InvitePreview(
  previewReference: 'preview-ref',
  expiresAt: DateTime.utc(2026, 9, 9, 10, 40),
  event: InviteEvent(
    title: 'Goa trip',
    eventAt: DateTime.utc(2030, 12, 20, 4, 30),
    eventTimeZone: 'Asia/Kolkata',
    theme: RoomTheme(
      kind: RoomThemeKind.preset,
      presetKey: 'soft-blue-01',
      overlayKey: 'dark-soft',
    ),
  ),
  inviter: const InviteInviter(displayName: 'Aarav'),
  memberCount: 8,
  requiresAuthentication: true,
);

Room room() => Room(
  id: 'room-id',
  title: 'Goa trip',
  eventAt: DateTime.utc(2030, 12, 20, 4, 30),
  eventTimeZone: 'Asia/Kolkata',
  theme: const RoomTheme(
    kind: RoomThemeKind.preset,
    presetKey: 'soft-blue-01',
    overlayKey: 'dark-soft',
  ),
  status: RoomStatus.active,
  role: RoomRole.member,
  memberCount: 2,
  revision: 4,
  createdAt: DateTime.utc(2026, 9, 9, 10),
  updatedAt: DateTime.utc(2026, 9, 9, 10, 30),
  serverNow: DateTime.utc(2026, 9, 9, 11),
);
