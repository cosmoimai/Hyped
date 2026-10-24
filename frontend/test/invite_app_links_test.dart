import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/app/router/app_router.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';
import 'package:hyped/features/invitations/data/app_links/android_invite_link_platform.dart';
import 'package:hyped/features/invitations/data/app_links/invite_link_validator.dart';
import 'package:hyped/features/invitations/data/invitation_api.dart';
import 'package:hyped/features/invitations/data/invitation_repository.dart';
import 'package:hyped/features/invitations/domain/invitation.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_controllers.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_providers.dart';
import 'package:hyped/features/policy/data/policy_api.dart';
import 'package:hyped/features/policy/presentation/policy_controller.dart';
import 'package:hyped/features/policy/presentation/policy_providers.dart';
import 'package:hyped/features/rooms/data/room_repository.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:mocktail/mocktail.dart';
import 'helpers/fakes.dart';
import 'invitation_controller_test.dart' as fixtures;

const host = 'links.hyped.example';
final token = 'A' * 43;
final otherToken = 'B' * 43;
String link(String value) => 'https://$host/invite/$value';
PendingInviteIntent intent(String value) =>
    PendingInviteIntent(kind: InviteKind.link, credential: value);
const channel = MethodChannel('hyped/android_invite_links');
Future<void> warm(Object? value) {
  final received = Completer<void>();
  ServicesBinding.instance.channelBuffers.push(
    channel.name,
    const StandardMethodCodec().encodeMethodCall(
      MethodCall('inviteLink', value),
    ),
    (_) => received.complete(),
  );
  return received.future;
}

class LinkAuthRepository extends Mock implements AuthRepository {}

class LinkIdentity extends Mock implements IdentityProvider {}

class LinkPolicyApi extends Mock implements PolicyApi {}

class LinkRoomRepository extends Mock implements RoomRepository {}

class LinkRepository extends InvitationRepository {
  LinkRepository() : super(InvitationApi(Dio(), Dio()));
  int previews = 0;
  int joins = 0;
  InvitationApiException? failure;
  Completer<InviteJoinResult>? joined;
  @override
  Future<InvitePreview> preview(PendingInviteIntent intent) async {
    previews++;
    if (failure != null) throw failure!;
    return fixtures.preview();
  }

  @override
  Future<InviteJoinResult> join(
    PendingInviteIntent intent,
    String previewReference,
  ) async {
    joins++;
    return joined?.future ??
        InviteJoinResult(room: fixtures.room(), created: true);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late AndroidInviteLinkPlatform platform;
  setUp(() {
    platform = AndroidInviteLinkPlatform(channel);
    messenger.setMockMethodCallHandler(
      channel,
      (call) async =>
          call.method == 'getInitialLink' ? {'host': host, 'uri': null} : null,
    );
    registerFallbackValue(const PolicyVersions('v1', 'v1', 'v1'));
  });
  tearDown(() {
    platform.close();
    messenger.setMockMethodCallHandler(channel, null);
  });
  test('canonicalizes scheme/host only and preserves token case', () {
    final value = '${'aB_-' * 10}xYz';
    final parsed = InviteLinkValidator(
      host,
    ).parse('HTTPS://LINKS.HYPED.EXAMPLE/invite/$value');
    expect(parsed, intent(value));
    expect(parsed!.location, '/invite/$value');
    expect(parsed.toString(), isNot(contains(value)));
  });
  test(
    'rejects foreign lookalike credential-bearing and noncanonical authorities',
    () {
      final authorities = [
        'evil.example',
        'sub.$host',
        '$host.evil.example',
        '$host.',
        'user@$host',
        '$host:443',
        '$host:8443',
        'links.hyped.examp1e',
        'línks.hyped.example',
        'links%2ehyped.example',
      ];
      for (var i = 0; i < authorities.length; i++) {
        expect(
          InviteLinkValidator(
            host,
          ).parse('https://${authorities[i]}/invite/$token'),
          isNull,
          reason: 'Rejected authority $i',
        );
      }
    },
  );
  test(
    'rejects unexpected schemes paths encoding traversal query fragments and oversized data',
    () {
      final invalid = [
        'http://$host/invite/$token',
        'hyped://$host/invite/$token',
        '//$host/invite/$token',
        'https://$host/Invite/$token',
        'https://$host/rooms/$token',
        '${link(token)}/',
        '${link(token)}/extra',
        '${link(token)}?x=1',
        '${link(token)}#x',
        'https://$host/invite/%41${'A' * 42}',
        'https://$host/a/../invite/$token',
        'https://$host//invite/$token',
        ' ${link(token)}',
        '${link(token)}\n',
        'https://$host/invite/${'A' * 42}',
        'https://$host/invite/${'A' * 44}',
        'https://$host/invite/${'A' * 42}=',
        'https://$host/invite/${'A' * 42}+',
        'https://$host/invite/${'A' * 2048}',
      ];
      for (var i = 0; i < invalid.length; i++) {
        expect(
          InviteLinkValidator(host).parse(invalid[i]),
          isNull,
          reason: 'Rejected URI $i',
        );
      }
    },
  );
  test('missing or malformed host configuration disables links', () {
    for (final configured in [
      '',
      'https://$host',
      '*.hyped.example',
      'LINKS.HYPED.EXAMPLE',
      'links..example',
    ]) {
      expect(InviteLinkValidator(configured).parse(link(token)), isNull);
    }
    expect(InviteLinkValidator(host).parse(null), isNull);
    expect(InviteLinkValidator(host).parse({'uri': link(token)}), isNull);
  });
  test(
    'cold native handoff validates before publishing pending intent',
    () async {
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => {'host': host, 'uri': link(token)},
      );
      final pending = PendingInviteController();
      await platform.initialize(pending.acceptExternal);
      expect(pending.intent, intent(token));
    },
  );
  test(
    'warm native delivery uses same validation and duplicate suppression',
    () async {
      final pending = PendingInviteController();
      var deliveries = 0;
      await platform.initialize((value) {
        if (pending.acceptExternal(value)) deliveries++;
      });
      await warm(link(token));
      await warm(link(token));
      await warm('https://evil.example/invite/$otherToken');
      expect(deliveries, 1);
      expect(pending.intent, intent(token));
      await warm(link(otherToken));
      expect(deliveries, 2);
      expect(pending.intent, intent(otherToken));
    },
  );
  test('newest warm link wins while cold handshake is still pending', () async {
    final handshake = Completer<Map<String, Object?>>();
    messenger.setMockMethodCallHandler(channel, (_) => handshake.future);
    final received = <PendingInviteIntent>[];
    final initialized = platform.initialize(received.add);
    await warm(link(otherToken));
    handshake.complete({'host': host, 'uri': link(token)});
    await initialized;
    expect(received, [intent(otherToken)]);
  });
  test(
    'logout cleanup fences a late cold handoff and allows only future new links',
    () async {
      final handshake = Completer<Map<String, Object?>>();
      messenger.setMockMethodCallHandler(
        channel,
        (call) => call.method == 'getInitialLink'
            ? handshake.future
            : Future.value(null),
      );
      final received = <PendingInviteIntent>[];
      final initialized = platform.initialize(received.add);
      await platform.clearPending();
      handshake.complete({'host': host, 'uri': link(token)});
      await initialized;
      expect(received, isEmpty);
      await warm(link(otherToken));
      expect(received, [intent(otherToken)]);
    },
  );
  test('disabled host ignores both initial and warm delivery', () async {
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => {'host': '', 'uri': link(token)},
    );
    final received = <PendingInviteIntent>[];
    await platform.initialize(received.add);
    await warm(link(token));
    expect(received, isEmpty);
  });
  test(
    'duplicate delivery cannot interrupt a pending authentication/consent flow',
    () {
      var now = DateTime.utc(2030);
      final pending = PendingInviteController(clock: () => now);
      expect(pending.acceptExternal(intent(token)), isTrue);
      final generation = pending.generation;
      now = now.add(const Duration(minutes: 10));
      expect(pending.acceptExternal(intent(token)), isFalse);
      expect(pending.generation, generation);
      pending.complete(intent(token));
      expect(pending.intent, isNull);
      expect(pending.acceptExternal(intent(token)), isFalse);
      now = now.add(const Duration(seconds: 31));
      expect(pending.acceptExternal(intent(token)), isTrue);
    },
  );
  test(
    'pending cleanup removes intent/digests and invalidates queued navigation',
    () {
      var clears = 0;
      final pending = PendingInviteController(onClear: () => clears++);
      pending.acceptExternal(intent(token));
      final generation = pending.generation;
      pending.clear();
      expect(pending.intent, isNull);
      expect(pending.generation, greaterThan(generation));
      expect(clears, 1);
      expect(pending.acceptExternal(intent(token)), isTrue);
      pending.acceptExternal(intent(otherToken));
      pending.complete(intent(token));
      expect(pending.intent, intent(otherToken));
    },
  );
  test(
    'one controller does not join again after successful membership response',
    () async {
      final repository = LinkRepository();
      final flow = InviteFlowController(repository, intent(token));
      await flow.loadPreview();
      expect(await flow.join(), isNotNull);
      expect(await flow.join(), isNotNull);
      expect(repository.joins, 1);
      flow.dispose();
    },
  );
  for (final state in ['expired', 'rotated']) {
    test(
      '$state valid-format link reports safe preview failure without auto-join',
      () async {
        final repository = LinkRepository()
          ..failure = const InvitationApiException(
            code: 'INVITATION_INVALID',
            statusCode: 404,
          );
        final flow = InviteFlowController(repository, intent(token));
        await flow.loadPreview();
        expect(flow.error, 'This invite is invalid, expired, or was rotated.');
        expect(flow.error, isNot(contains(token)));
        expect(repository.joins, 0);
        flow.dispose();
      },
    );
  }
  testWidgets(
    'cold external invite survives initialization sign-in and real consent before explicit join',
    (tester) async {
      final repository = LinkAuthRepository();
      final restore = Completer<SessionRestoreResult>();
      when(
        () => repository.restoreSession(any()),
      ).thenAnswer((_) => restore.future);
      final identity = LinkIdentity();
      when(() => repository.signIn(identity)).thenAnswer((_) async {});
      when(repository.logout).thenAnswer((_) async {});
      final auth = AuthController(repository);
      final initializing = auth.initialize();
      final tokensStore = MemoryTokenStore(tokens());
      final policyApi = LinkPolicyApi();
      const required = PolicyStatus(
        PolicyVersions('2026-10-22', '2026-10-22', '2026-10-22'),
        available: true,
        accepted: false,
      );
      when(() => policyApi.status(any())).thenAnswer((_) async => required);
      when(() => policyApi.accept(any(), any())).thenAnswer(
        (_) async =>
            PolicyStatus(required.required, available: true, accepted: true),
      );
      final policy = PolicyController(policyApi, auth, tokensStore);
      final pending = PendingInviteController();
      final invites = LinkRepository();
      final rooms = LinkRoomRepository();
      when(
        () => rooms.getRoom('room-id'),
      ).thenAnswer((_) async => fixtures.room());
      final router = createRouter(auth, pending, policy);
      addTearDown(router.dispose);
      void deliver(PendingInviteIntent value) {
        if (pending.acceptExternal(value)) router.go(value.location);
      }

      messenger.setMockMethodCallHandler(
        channel,
        (_) async => {'host': host, 'uri': link(token)},
      );
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authControllerProvider.overrideWith((_) => auth),
            policyControllerProvider.overrideWith((_) => policy),
            pendingInviteControllerProvider.overrideWith((_) => pending),
            invitationRepositoryProvider.overrideWith((_) => invites),
            roomRepositoryProvider.overrideWith((_) => rooms),
          ],
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await platform.initialize(deliver);
      await tester.pumpAndSettle();
      expect(router.routeInformationProvider.value.uri.path, '/invite/$token');
      expect(find.text('Goa trip'), findsOneWidget);
      restore.complete(SessionRestoreResult.missing);
      await initializing;
      await tester.pumpAndSettle();
      await tester.tap(find.text('Sign in to join'));
      await tester.pumpAndSettle();
      await warm(link(token));
      await tester.pumpAndSettle();
      expect(router.routeInformationProvider.value.uri.path, '/sign-in');
      await auth.signIn(identity);
      await tester.pumpAndSettle();
      expect(
        router.routeInformationProvider.value.uri.path,
        '/policy-acceptance',
      );
      await warm(link(token));
      await tester.pumpAndSettle();
      expect(
        router.routeInformationProvider.value.uri.path,
        '/policy-acceptance',
      );
      await policy.accept(terms: true, privacy: true, adult: true);
      await tester.pumpAndSettle();
      expect(router.routeInformationProvider.value.uri.path, '/invite/$token');
      expect(invites.joins, 0);
      await tester.tap(find.text('Join room'));
      await tester.pumpAndSettle();
      expect(router.routeInformationProvider.value.uri.path, '/rooms/room-id');
      expect(pending.intent, isNull);
      expect(invites.joins, 1);
      await warm(link(token));
      await tester.pumpAndSettle();
      expect(router.routeInformationProvider.value.uri.path, '/rooms/room-id');
      expect(invites.joins, 1);
    },
  );
  testWidgets(
    'warm different invite replaces pending destination while already signed in',
    (tester) async {
      final repository = LinkAuthRepository();
      when(
        () => repository.restoreSession(any()),
      ).thenAnswer((_) async => SessionRestoreResult.authenticated);
      final auth = AuthController(repository);
      await auth.initialize();
      final pending = PendingInviteController();
      final invites = LinkRepository();
      final router = createRouter(auth, pending);
      addTearDown(router.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authControllerProvider.overrideWith((_) => auth),
            pendingInviteControllerProvider.overrideWith((_) => pending),
            invitationRepositoryProvider.overrideWith((_) => invites),
          ],
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await platform.initialize((value) {
        if (pending.acceptExternal(value)) router.go(value.location);
      });
      await warm(link(token));
      await tester.pumpAndSettle();
      await warm(link(otherToken));
      await tester.pumpAndSettle();
      expect(
        router.routeInformationProvider.value.uri.path,
        '/invite/$otherToken',
      );
      expect(pending.intent, intent(otherToken));
      expect(invites.joins, 0);
    },
  );
}
