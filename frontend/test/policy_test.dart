import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/app/router/app_router.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';
import 'package:hyped/features/invitations/domain/invitation.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_controllers.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_providers.dart';
import 'package:hyped/features/invitations/data/invitation_repository.dart';
import 'package:hyped/features/onboarding/presentation/screens/onboarding_screen.dart';
import 'package:hyped/features/policy/data/policy_api.dart';
import 'package:hyped/features/policy/presentation/legal_document_screen.dart';
import 'package:hyped/features/policy/presentation/policy_controller.dart';
import 'package:hyped/features/policy/presentation/policy_providers.dart';
import 'package:hyped/features/policy/presentation/policy_screen.dart';
import 'package:hyped/features/rooms/data/room_repository.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:mocktail/mocktail.dart';
import 'helpers/fakes.dart';

class MockPolicyApi extends Mock implements PolicyApi {}

class MockPolicyAuthRepository extends Mock implements AuthRepository {}

class MockPolicyIdentity extends Mock implements IdentityProvider {}

class MockPolicyRooms extends Mock implements RoomRepository {}

class MockPolicyInvites extends Mock implements InvitationRepository {}

const versions = PolicyVersions('2026-10-22', '2026-10-22', '2026-10-22');
const requiredPolicy = PolicyStatus(versions, available: true, accepted: false);
const acceptedPolicy = PolicyStatus(versions, available: true, accepted: true);

void main() {
  late MockPolicyApi api;
  late MockPolicyAuthRepository repository;
  late AuthController auth;
  late MemoryTokenStore store;
  late PolicyController policy;
  bool providerOwnsPolicy = false;
  bool providerOwnsAuth = false;
  setUp(() async {
    providerOwnsPolicy = false;
    providerOwnsAuth = false;
    registerFallbackValue(versions);
    registerFallbackValue(
      const PendingInviteIntent(kind: InviteKind.link, credential: 'fallback'),
    );
    api = MockPolicyApi();
    repository = MockPolicyAuthRepository();
    store = MemoryTokenStore(tokens());
    when(
      () => repository.restoreSession(any()),
    ).thenAnswer((_) async => SessionRestoreResult.authenticated);
    when(repository.logout).thenAnswer((_) async {
      await store.clear();
    });
    auth = AuthController(repository);
    await auth.initialize();
    when(() => api.status(any())).thenAnswer((_) async => requiredPolicy);
    when(
      () => api.accept(any(), any()),
    ).thenAnswer((_) async => acceptedPolicy);
    policy = PolicyController(api, auth, store);
    await Future<void>.delayed(Duration.zero);
  });
  tearDown(() {
    if (!providerOwnsPolicy) policy.dispose();
    if (!providerOwnsAuth) auth.dispose();
  });

  test('new account has required unchecked consent', () {
    expect(policy.phase, PolicyPhase.required);
    expect(policy.accepted, isFalse);
  });
  test('existing accepted account is not prompted again', () async {
    when(() => api.status(any())).thenAnswer((_) async => acceptedPolicy);
    await policy.load();
    expect(policy.accepted, isTrue);
    verifyNever(() => api.accept(any(), any()));
  });
  test('foreground recheck does not reprompt an accepted account', () async {
    when(() => api.status(any())).thenAnswer((_) async => acceptedPolicy);
    await policy.load();
    final response = Completer<PolicyStatus>();
    when(() => api.status(any())).thenAnswer((_) => response.future);
    final refresh = policy.load();
    expect(policy.accepted, isTrue);
    response.complete(acceptedPolicy);
    await refresh;
    expect(policy.accepted, isTrue);
  });
  test('updated required version reopens consent', () async {
    when(() => api.status(any())).thenAnswer((_) async => acceptedPolicy);
    await policy.load();
    when(() => api.status(any())).thenAnswer(
      (_) async => const PolicyStatus(
        PolicyVersions('2026-11-01', '2026-10-22', '2026-11-01'),
        available: true,
        accepted: false,
      ),
    );
    await policy.load();
    expect(policy.accepted, isFalse);
    expect(policy.status!.required.terms, '2026-11-01');
  });
  test('all three choices required before submission', () async {
    for (final flags in [
      [false, true, true],
      [true, false, true],
      [true, true, false],
    ]) {
      expect(
        await policy.accept(
          terms: flags[0],
          privacy: flags[1],
          adult: flags[2],
        ),
        isFalse,
      );
    }
    verifyNever(() => api.accept(any(), any()));
  });
  test('network failure does not imply acceptance and permits retry', () async {
    when(
      () => api.accept(any(), any()),
    ).thenThrow(const PolicyApiException(null));
    expect(
      await policy.accept(terms: true, privacy: true, adult: true),
      isFalse,
    );
    expect(policy.accepted, isFalse);
    expect(policy.error, isNotNull);
    when(
      () => api.accept(any(), any()),
    ).thenAnswer((_) async => acceptedPolicy);
    expect(
      await policy.accept(terms: true, privacy: true, adult: true),
      isTrue,
    );
    expect(policy.accepted, isTrue);
  });
  test(
    'version conflict reloads current status without resubmitting',
    () async {
      when(
        () => api.accept(any(), any()),
      ).thenThrow(const PolicyApiException('POLICY_VERSION_CHANGED'));
      when(() => api.status(any())).thenAnswer(
        (_) async => const PolicyStatus(
          PolicyVersions('2026-11-01', '2026-11-01', '2026-11-01'),
          available: true,
          accepted: false,
        ),
      );
      expect(
        await policy.accept(terms: true, privacy: true, adult: true),
        isFalse,
      );
      expect(policy.saving, isFalse);
      expect(policy.status!.required.terms, '2026-11-01');
      verify(() => api.accept(any(), any())).called(1);
    },
  );
  test('draft documents cannot be accepted', () async {
    when(() => api.status(any())).thenAnswer(
      (_) async =>
          const PolicyStatus(versions, available: false, accepted: false),
    );
    await policy.load();
    expect(
      await policy.accept(terms: true, privacy: true, adult: true),
      isFalse,
    );
    verifyNever(() => api.accept(any(), any()));
  });
  test(
    'logout discards server-derived state and ignores late acceptance',
    () async {
      final response = Completer<PolicyStatus>();
      when(() => api.accept(any(), any())).thenAnswer((_) => response.future);
      final pending = policy.accept(terms: true, privacy: true, adult: true);
      await Future<void>.delayed(Duration.zero);
      await auth.logout();
      response.complete(acceptedPolicy);
      expect(await pending, isFalse);
      expect(policy.phase, PolicyPhase.signedOut);
      expect(policy.status, isNull);
    },
  );
  test('status outage fails closed and retries', () async {
    when(() => api.status(any())).thenThrow(const PolicyApiException(null));
    await policy.load();
    expect(policy.phase, PolicyPhase.failure);
    expect(policy.accepted, isFalse);
    when(() => api.status(any())).thenAnswer((_) async => acceptedPolicy);
    await policy.load();
    expect(policy.accepted, isTrue);
  });

  testWidgets('checkboxes begin unchecked and continue needs every choice', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          policyControllerProvider.overrideWith((_) {
            providerOwnsPolicy = true;
            return policy;
          }),
          authControllerProvider.overrideWith((_) {
            providerOwnsAuth = true;
            return auth;
          }),
        ],
        child: const MaterialApp(home: PolicyScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      tester
          .widgetList<CheckboxListTile>(find.byType(CheckboxListTile))
          .every((box) => box.value == false),
      isTrue,
    );
    expect(
      tester
          .widget<FilledButton>(
            find.widgetWithText(FilledButton, 'Accept and continue'),
          )
          .onPressed,
      isNull,
    );
    for (final label in [
      'I accept the Terms of Service and Content Rules.',
      'I accept the Privacy Policy.',
      'I confirm that I am at least 18 years old.',
    ]) {
      await tester.ensureVisible(find.text(label));
      await tester.tap(find.text(label));
      await tester.pumpAndSettle();
    }
    final button = find.widgetWithText(FilledButton, 'Accept and continue');
    await tester.ensureVisible(button);
    expect(tester.widget<FilledButton>(button).onPressed, isNotNull);
    await tester.tap(button);
    await tester.pumpAndSettle();
    expect(policy.accepted, isTrue);
  });
  testWidgets('changing version resets all checked choices', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          policyControllerProvider.overrideWith((_) {
            providerOwnsPolicy = true;
            return policy;
          }),
        ],
        child: const MaterialApp(home: PolicyScreen()),
      ),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('I accept the Privacy Policy.'));
    await tester.tap(find.text('I accept the Privacy Policy.'));
    await tester.pumpAndSettle();
    when(() => api.status(any())).thenAnswer(
      (_) async => const PolicyStatus(
        PolicyVersions('2026-11-01', '2026-11-01', '2026-11-01'),
        available: true,
        accepted: false,
      ),
    );
    await policy.load();
    await tester.pumpAndSettle();
    expect(
      tester
          .widgetList<CheckboxListTile>(find.byType(CheckboxListTile))
          .every((box) => box.value == false),
      isTrue,
    );
  });
  for (final target in ['/home', '/rooms/new']) {
    testWidgets('unaccepted $target is gated without redirect loops', (
      tester,
    ) async {
      final router = createRouter(auth, null, policy)..go(target);
      addTearDown(router.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authControllerProvider.overrideWith((_) {
              providerOwnsAuth = true;
              return auth;
            }),
            policyControllerProvider.overrideWith((_) {
              providerOwnsPolicy = true;
              return policy;
            }),
          ],
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        router.routeInformationProvider.value.uri.path,
        '/policy-acceptance',
      );
      expect(find.text('Your choices, clearly.'), findsOneWidget);
    });
  }
  testWidgets('accepted account reaches home and expiry returns to sign-in', (
    tester,
  ) async {
    when(() => api.status(any())).thenAnswer((_) async => acceptedPolicy);
    await policy.load();
    final rooms = MockPolicyRooms();
    when(rooms.listRooms).thenAnswer((_) async => []);
    final router = createRouter(auth, null, policy)..go('/sign-in');
    addTearDown(router.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authControllerProvider.overrideWith((_) {
            providerOwnsAuth = true;
            return auth;
          }),
          policyControllerProvider.overrideWith((_) {
            providerOwnsPolicy = true;
            return policy;
          }),
          roomRepositoryProvider.overrideWith((_) => rooms),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();
    expect(router.routeInformationProvider.value.uri.path, '/home');
    await auth.logout();
    await tester.pumpAndSettle();
    expect(router.routeInformationProvider.value.uri.path, '/sign-in');
  });
  testWidgets('sign-in then consent returns to same pending invite', (
    tester,
  ) async {
    await auth.logout();
    await store.write(tokens());
    final identity = MockPolicyIdentity();
    when(() => repository.signIn(identity)).thenAnswer((_) async {});
    final pending = PendingInviteController()
      ..remember(
        const PendingInviteIntent(
          kind: InviteKind.link,
          credential: 'private-token',
        ),
      );
    final invites = MockPolicyInvites();
    when(() => invites.preview(any())).thenThrow(Exception('offline'));
    registerFallbackValue(
      const PendingInviteIntent(kind: InviteKind.link, credential: 'fallback'),
    );
    final router = createRouter(auth, pending, policy)..go('/sign-in');
    addTearDown(router.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authControllerProvider.overrideWith((_) {
            providerOwnsAuth = true;
            return auth;
          }),
          policyControllerProvider.overrideWith((_) {
            providerOwnsPolicy = true;
            return policy;
          }),
          pendingInviteControllerProvider.overrideWith((_) => pending),
          invitationRepositoryProvider.overrideWith((_) => invites),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();
    await auth.signIn(identity);
    await tester.pumpAndSettle();
    expect(
      router.routeInformationProvider.value.uri.path,
      '/policy-acceptance',
    );
    await policy.accept(terms: true, privacy: true, adult: true);
    await tester.pumpAndSettle();
    expect(
      router.routeInformationProvider.value.uri.path,
      '/invite/private-token',
    );
    expect(pending.intent?.credential, 'private-token');
  });
  testWidgets(
    'public legal document works before sign-in and on initialization',
    (tester) async {
      when(() => api.document('privacy', null)).thenAnswer(
        (_) async => const LegalDocument(
          'Privacy Policy',
          '2026-10-22',
          'Readable policy text',
          false,
        ),
      );
      final router = createRouter(AuthController(repository), null, policy)
        ..go('/legal/privacy');
      addTearDown(router.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [policyApiProvider.overrideWith((_) => api)],
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Readable policy text'), findsOneWidget);
      expect(find.text('Draft — not yet published'), findsOneWidget);
    },
  );
  testWidgets('legal document failure has safe retry', (tester) async {
    when(
      () => api.document('terms', null),
    ).thenAnswer((_) async => throw const PolicyApiException(null));
    await tester.pumpWidget(
      ProviderScope(
        overrides: [policyApiProvider.overrideWith((_) => api)],
        child: const MaterialApp(home: LegalDocumentScreen(kind: 'terms')),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.text('Could not load this document. Please retry.'),
      findsOneWidget,
    );
    when(() => api.document('terms', null)).thenAnswer(
      (_) async =>
          const LegalDocument('Terms', '2026-10-22', 'Terms text', true),
    );
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('Terms text'), findsOneWidget);
  });
  testWidgets(
    'interactive demo ticks, changes theme and preserves skip intent',
    (tester) async {
      final pending = PendingInviteController()
        ..remember(
          const PendingInviteIntent(
            kind: InviteKind.link,
            credential: 'pending-token',
          ),
        );
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            pendingInviteControllerProvider.overrideWith((_) => pending),
          ],
          child: const MaterialApp(home: OnboardingScreen()),
        ),
      );
      expect(find.text('Summer trip'), findsOneWidget);
      await tester.tap(find.text('Midnight'));
      await tester.pump(const Duration(milliseconds: 200));
      expect(
        tester
            .widget<ChoiceChip>(find.widgetWithText(ChoiceChip, 'Midnight'))
            .selected,
        isTrue,
      );
      await tester.pump(const Duration(seconds: 1));
      expect(find.text('Skip to invite'), findsOneWidget);
      expect(pending.intent?.credential, 'pending-token');
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
  testWidgets(
    'skip demo opens the same public invite without sign-in or consent',
    (tester) async {
      await auth.logout();
      final pending = PendingInviteController()
        ..remember(
          const PendingInviteIntent(
            kind: InviteKind.link,
            credential: 'skip-token',
          ),
        );
      final invites = MockPolicyInvites();
      when(
        () => invites.preview(any()),
      ).thenAnswer((_) async => throw Exception('offline'));
      final router = createRouter(auth, pending, policy)..go('/onboarding');
      addTearDown(router.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authControllerProvider.overrideWith((_) {
              providerOwnsAuth = true;
              return auth;
            }),
            pendingInviteControllerProvider.overrideWith((_) => pending),
            policyControllerProvider.overrideWith((_) {
              providerOwnsPolicy = true;
              return policy;
            }),
            invitationRepositoryProvider.overrideWith((_) => invites),
          ],
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Skip to invite'),
        150,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(find.text('Skip to invite'));
      await tester.pumpAndSettle();
      expect(
        router.routeInformationProvider.value.uri.path,
        '/invite/skip-token',
      );
      expect(pending.intent?.credential, 'skip-token');
      expect(policy.phase, PolicyPhase.signedOut);
      verifyNever(() => api.accept(any(), any()));
    },
  );
  testWidgets(
    'authentication initialization keeps public preview without redirect loops',
    (tester) async {
      final initial = AuthController(repository);
      final invites = MockPolicyInvites();
      when(
        () => invites.preview(any()),
      ).thenAnswer((_) async => throw Exception('offline'));
      final router = createRouter(initial, null, policy)
        ..go('/invite/initial-token');
      addTearDown(router.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authControllerProvider.overrideWith((_) => initial),
            invitationRepositoryProvider.overrideWith((_) => invites),
          ],
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        router.routeInformationProvider.value.uri.path,
        '/invite/initial-token',
      );
      expect(find.text('Try again'), findsOneWidget);
    },
  );
  test(
    'acceptance API sends no identity/time and creates fresh UUID keys',
    () async {
      final public = Dio();
      final secured = Dio();
      final adapter = DioAdapter(dio: secured);
      final requests = <RequestOptions>[];
      secured.interceptors.add(
        InterceptorsWrapper(
          onRequest: (options, handler) {
            requests.add(options);
            handler.next(options);
          },
        ),
      );
      adapter.onPost(
        '/me/policy-acceptances',
        (server) => server.reply(200, {
          'required': {
            'termsVersion': '2026-10-22',
            'privacyVersion': '2026-10-22',
            'contentRulesVersion': '2026-10-22',
          },
          'documentsAvailable': true,
          'currentAccepted': true,
        }),
        data: Matchers.any,
      );
      final real = PolicyApi(public, secured);
      await real.accept(versions, 'session');
      await real.accept(versions, 'session');
      expect(
        (requests.first.data as Map).keys,
        unorderedEquals([
          'termsVersion',
          'privacyVersion',
          'contentRulesVersion',
          'accepted',
          'privacyAccepted',
          'adultAffirmed',
        ]),
      );
      final keys = requests
          .map((value) => value.headers['Idempotency-Key'] as String)
          .toList();
      expect(
        keys[0],
        matches(
          RegExp(
            r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
          ),
        ),
      );
      expect(keys[0], isNot(keys[1]));
      expect(requests.first.extra['expectedSessionId'], 'session');
    },
  );
}
