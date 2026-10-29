import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:hyped/core/api/authentication_interceptor.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/invitations/data/invitation_api.dart';
import 'package:hyped/features/invitations/data/invitation_share_platform.dart';
import 'package:hyped/features/invitations/domain/share_invitation.dart';
import 'package:hyped/features/invitations/presentation/controllers/share_invitation_controller.dart';
import 'package:hyped/features/invitations/presentation/screens/share_invite_sheet.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/rooms/presentation/screens/room_detail_screen.dart';
import 'package:mocktail/mocktail.dart';
import 'helpers/fakes.dart';
import 'room_widgets_test.dart' as fixtures;

class MockInvitations extends Mock implements InvitationApi {}

class ShareSpy extends InvitationSharePlatform {
  final values = <ShareInvitation>[];
  bool fail = false;
  @override
  Future<void> share(ShareInvitation invitation) async {
    if (fail) throw StateError('sensitive native failure');
    values.add(invitation);
  }
}

class EpochWidgets extends NoopRoomWidgetRepository {
  int epoch = 0;
  @override
  int get sessionEpoch => epoch;
}

const invitation = ShareInvitation(
  roomCode: '7K4M-P9QX',
  inviteUrl: 'https://localhost:8443/invite/secret',
);

void main() {
  late MockInvitations api;
  late MemoryTokenStore tokensStore;
  late ShareSpy platform;
  late EpochWidgets widgets;
  late ShareInvitationController controller;
  setUp(() {
    api = MockInvitations();
    tokensStore = MemoryTokenStore(tokens());
    platform = ShareSpy();
    widgets = EpochWidgets();
    controller = ShareInvitationController(
      api,
      tokensStore,
      platform,
      widgets,
      'room',
    );
    when(
      () => api.currentInvitation('room', 'session-one'),
    ).thenAnswer((_) async => invitation);
  });

  test(
    'current invitation uses authenticated Dio with a fenced session',
    () async {
      final auth = Dio(BaseOptions(baseUrl: 'https://api.test'));
      final public = Dio();
      final adapter = DioAdapter(dio: auth);
      auth.httpClientAdapter = adapter;
      auth.interceptors.add(
        AuthenticationInterceptor(auth, tokensStore, FakeRefresher(tokens())),
      );
      adapter.onGet(
        '/rooms/room/invitation',
        (server) => server.reply(200, {
          'roomCode': invitation.roomCode,
          'inviteUrl': invitation.inviteUrl,
          'generation': 1,
          'validUntil': 'ROOM_END',
        }),
        headers: {'Authorization': 'Bearer access-one'},
      );
      final result = await InvitationApi(
        public,
        auth,
      ).currentInvitation('room', 'session-one');
      expect(result.roomCode, invitation.roomCode);
      await expectLater(
        InvitationApi(public, auth).currentInvitation('room', 'other-session'),
        throwsA(isA<InvitationApiException>()),
      );
    },
  );

  test('malformed response and diagnostics never echo credentials', () async {
    expect(
      () => ShareInvitation.fromJson({'roomCode': 'private malformed code'}),
      throwsA(
        isA<FormatException>().having(
          (error) => error.toString(),
          'sanitized',
          isNot(contains('private malformed')),
        ),
      ),
    );
    expect(invitation.toString(), 'ShareInvitation[REDACTED]');
    expect(
      const InvitationApiException().toString(),
      isNot(contains('secret')),
    );
  });

  test('loads code without opening an external share target', () async {
    await controller.load();
    expect(controller.invitation, invitation);
    expect(platform.values, isEmpty);
    controller.dispose();
  });
  test('share reauthorizes and uses newest rotated credentials', () async {
    await controller.load();
    const replacement = ShareInvitation(roomCode: 'ABCD-EFGH');
    when(
      () => api.currentInvitation('room', 'session-one'),
    ).thenAnswer((_) async => replacement);
    await controller.share();
    expect(platform.values, [replacement]);
    expect(controller.invitation, replacement);
    verify(() => api.currentInvitation('room', 'session-one')).called(2);
    controller.dispose();
  });
  test('repeated taps make one request and one share handoff', () async {
    final pending = Completer<ShareInvitation>();
    when(
      () => api.currentInvitation('room', 'session-one'),
    ).thenAnswer((_) => pending.future);
    final operation = controller.share();
    await controller.share();
    pending.complete(invitation);
    await operation;
    expect(platform.values.length, 1);
    controller.dispose();
  });
  for (final status in [401, 403, 404, 410, 500]) {
    test('HTTP $status clears credentials and never shares', () async {
      controller.invitation = invitation;
      when(
        () => api.currentInvitation('room', 'session-one'),
      ).thenThrow(InvitationApiException(statusCode: status));
      await controller.share();
      expect(controller.invitation, isNull);
      expect(controller.error, isNotNull);
      expect(controller.error, isNot(contains(invitation.roomCode)));
      expect(platform.values, isEmpty);
      controller.dispose();
    });
  }
  test('offline failure never shares previously loaded credentials', () async {
    controller.invitation = invitation;
    when(
      () => api.currentInvitation('room', 'session-one'),
    ).thenThrow(const InvitationApiException(code: 'OFFLINE_READ_ONLY'));
    await controller.share();
    expect(controller.error, contains('internet'));
    expect(controller.invitation, isNull);
    expect(platform.values, isEmpty);
    controller.dispose();
  });
  for (final change in [
    'logout',
    'account change',
    'widget epoch',
    'dispose',
    'auth invalidation',
  ]) {
    test('$change fences a delayed response', () async {
      final pending = Completer<ShareInvitation>();
      when(
        () => api.currentInvitation('room', 'session-one'),
      ).thenAnswer((_) => pending.future);
      final operation = controller.share();
      await Future<void>.delayed(Duration.zero);
      switch (change) {
        case 'logout':
          await tokensStore.clear();
        case 'account change':
          await tokensStore.write(tokens('two'));
        case 'widget epoch':
          widgets.epoch++;
        case 'dispose':
          controller.dispose();
        case 'auth invalidation':
          controller.invalidate();
      }
      pending.complete(invitation);
      await operation;
      expect(controller.invitation, isNull);
      expect(platform.values, isEmpty);
      if (change != 'dispose') {
        expect(controller.busy, isFalse);
        controller.dispose();
      }
    });
  }
  test('platform failures expose only a clean retry message', () async {
    platform.fail = true;
    await controller.share();
    expect(controller.error, isNot(contains('sensitive')));
    expect(controller.invitation, isNull);
    controller.dispose();
  });

  for (final role in RoomRole.values) {
    testWidgets('$role can see Share invite on an active room', (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            roomRepositoryProvider.overrideWithValue(
              fixtures.repositoryWithDetail(fixtures.room(role: role)),
            ),
          ],
          child: const MaterialApp(home: RoomDetailScreen(roomId: 'room-id')),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Share invite'), findsOneWidget);
    });
  }
  for (final state in ['archived', 'cached']) {
    testWidgets('$state room has no share action', (tester) async {
      final room = fixtures.room().copyWith(
        status: state == 'archived' ? RoomStatus.archived : RoomStatus.active,
        isCached: state == 'cached',
      );
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            roomRepositoryProvider.overrideWithValue(
              fixtures.repositoryWithDetail(room),
            ),
          ],
          child: const MaterialApp(home: RoomDetailScreen(roomId: 'room-id')),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Share invite'), findsNothing);
    });
  }
  testWidgets(
    'room button opens code sheet and only explicit share launches platform',
    (tester) async {
      await controller.load();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            roomRepositoryProvider.overrideWithValue(
              fixtures.repositoryWithDetail(fixtures.room()),
            ),
            shareInvitationControllerProvider.overrideWith(
              (ref, id) => controller,
            ),
          ],
          child: const MaterialApp(home: RoomDetailScreen(roomId: 'room-id')),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Share invite'));
      await tester.pumpAndSettle();
      expect(find.text('Invite your people'), findsOneWidget);
      expect(find.text(invitation.roomCode), findsOneWidget);
      expect(find.text(invitation.inviteUrl!), findsNothing);
      expect(platform.values, isEmpty);
      await tester.tap(
        find.descendant(
          of: find.byType(ShareInviteSheet),
          matching: find.text('Share invite'),
        ),
      );
      await tester.pumpAndSettle();
      expect(platform.values, [invitation]);
      await tester.tap(find.text('Close'));
      await tester.pumpAndSettle();
      expect(controller.invitation, isNull);
    },
  );
  testWidgets('failed sheet offers retry without leaking backend diagnostics', (
    tester,
  ) async {
    controller.error = 'Could not share this invitation. Please try again.';
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          shareInvitationControllerProvider.overrideWith(
            (ref, id) => controller,
          ),
        ],
        child: const MaterialApp(
          home: Scaffold(body: ShareInviteSheet(roomId: 'room')),
        ),
      ),
    );
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text(invitation.roomCode), findsOneWidget);
    expect(platform.values, isEmpty);
  });
}
