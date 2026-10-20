import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/presentation/controllers/member_controller.dart';
import 'package:hyped/features/rooms/data/member_api.dart';
import 'package:hyped/features/rooms/domain/room_member.dart';
import 'package:hyped/features/rooms/presentation/screens/member_screen.dart';
import 'package:mocktail/mocktail.dart';

class MockMemberApi extends Mock implements MemberApi {}

class RecordingWidgets extends NoopRoomWidgetRepository {
  final List<String> states = [];
  int epoch = 0;
  @override
  int get sessionEpoch => epoch;
  @override
  Future<void> markRoom(
    String roomId,
    String state, {
    required int epoch,
  }) async {
    states.add('$roomId:$state');
  }
}

const owner = RoomMember(
  userId: 'owner',
  displayName: 'Owner',
  role: RoomRole.owner,
);
const coHost = RoomMember(
  userId: 'host',
  displayName: 'Co-host',
  role: RoomRole.coHost,
);
const member = RoomMember(
  userId: 'member',
  displayName: 'Mira',
  role: RoomRole.member,
);
MemberSnapshot view([RoomRole role = RoomRole.owner, bool active = true]) =>
    MemberSnapshot([owner, coHost, member], role, active, '"room-4"');

void main() {
  test(
    'owner can promote demote remove co-host and transfer but cannot modify owner or self',
    () {
      expect(memberActions(view(), member), [
        MemberAction.promote,
        MemberAction.transfer,
        MemberAction.remove,
      ]);
      expect(memberActions(view(), coHost), [
        MemberAction.demote,
        MemberAction.transfer,
        MemberAction.remove,
      ]);
      expect(memberActions(view(), owner), isEmpty);
      expect(
        memberActions(
          view(),
          const RoomMember(
            userId: 'me',
            displayName: 'Me',
            role: RoomRole.coHost,
            isCurrentUser: true,
          ),
        ),
        isEmpty,
      );
    },
  );
  test('co-host can only remove regular members', () {
    expect(memberActions(view(RoomRole.coHost), member), [MemberAction.remove]);
    expect(memberActions(view(RoomRole.coHost), coHost), isEmpty);
    expect(memberActions(view(RoomRole.coHost), owner), isEmpty);
  });
  test('members and archived rooms have no management actions', () {
    expect(memberActions(view(RoomRole.member), member), isEmpty);
    expect(memberActions(view(RoomRole.owner, false), member), isEmpty);
  });
  test(
    'API loads member projection and sends correct revision and mutation contracts',
    () async {
      final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
      final adapter = DioAdapter(dio: dio);
      dio.httpClientAdapter = adapter;
      final api = MemberApi(dio);
      adapter.onGet(
        '/rooms/r/members',
        (server) => server.reply(
          200,
          {
            'items': [
              {
                'userId': 'm',
                'displayName': 'Mira',
                'role': 'MEMBER',
                'isCurrentUser': true,
              },
            ],
            'callerRole': 'OWNER',
            'status': 'ACTIVE',
            'revision': 4,
          },
          headers: {
            'etag': ['"room-4"'],
            'content-type': ['application/json'],
          },
        ),
      );
      final snapshot = await api.load('r');
      expect(snapshot.items.single.displayName, 'Mira');
      expect(snapshot.items.single.isCurrentUser, isTrue);
      expect(snapshot.etag, '"room-4"');
      adapter.onPatch(
        '/rooms/r/members/m',
        (server) => server.reply(200, {}),
        data: {'role': 'CO_HOST'},
        headers: {'If-Match': '"room-4"'},
      );
      await api.change('r', 'm', MemberAction.promote, snapshot.etag);
      adapter.onPatch(
        '/rooms/r/members/m',
        (server) => server.reply(200, {}),
        data: {'role': 'MEMBER'},
        headers: {'If-Match': '"room-4"'},
      );
      await api.change('r', 'm', MemberAction.demote, snapshot.etag);
      adapter.onDelete(
        '/rooms/r/members/m',
        (server) => server.reply(204, null),
        headers: {'If-Match': '"room-4"'},
      );
      await api.change('r', 'm', MemberAction.remove, snapshot.etag);
      adapter.onPost(
        '/rooms/r/ownership-transfer',
        (server) => server.reply(200, {}),
        data: {'newOwnerUserId': 'm'},
        headers: {'If-Match': '"room-4"'},
      );
      await api.change('r', 'm', MemberAction.transfer, snapshot.etag);
    },
  );
  test('API maps errors without exposing backend diagnostic text', () async {
    final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
    final adapter = DioAdapter(dio: dio);
    dio.httpClientAdapter = adapter;
    adapter.onGet(
      '/rooms/r/members',
      (server) => server.reply(403, {
        'code': 'ROOM_ACTION_FORBIDDEN',
        'detail': 'private-data',
      }),
    );
    await expectLater(
      MemberApi(dio).load('r'),
      throwsA(
        isA<RoomApiException>().having(
          (e) => memberErrorMessage(e),
          'safe message',
          isNot(contains('private-data')),
        ),
      ),
    );
  });
  test('controller reloads permissions after ownership transfer', () async {
    final api = MockMemberApi();
    var result = view();
    when(() => api.load('r')).thenAnswer((_) async => result);
    when(
      () => api.change('r', 'member', MemberAction.transfer, '"room-4"'),
    ).thenAnswer((_) async {
      result = view(RoomRole.coHost);
    });
    final controller = MemberController(api, 'r');
    await controller.load();
    expect(await controller.change(member, MemberAction.transfer), isTrue);
    expect(controller.snapshot!.callerRole, RoomRole.coHost);
    expect(memberActions(controller.snapshot!, coHost), isEmpty);
    controller.dispose();
  });
  test(
    'controller prevents unauthorized and duplicate in-flight submissions',
    () async {
      final api = MockMemberApi();
      when(() => api.load('r')).thenAnswer((_) async => view());
      final pending = Completer<void>();
      when(
        () => api.change('r', 'member', MemberAction.remove, '"room-4"'),
      ).thenAnswer((_) => pending.future);
      final controller = MemberController(api, 'r');
      await controller.load();
      expect(await controller.change(owner, MemberAction.remove), isFalse);
      final operation = controller.change(member, MemberAction.remove);
      expect(controller.saving, isTrue);
      expect(await controller.change(member, MemberAction.remove), isFalse);
      pending.complete();
      expect(await operation, isTrue);
      verify(
        () => api.change('r', 'member', MemberAction.remove, '"room-4"'),
      ).called(1);
      controller.dispose();
    },
  );
  test('revision errors disable actions until reload', () async {
    final api = MockMemberApi();
    when(() => api.load('r')).thenAnswer((_) async => view());
    when(
      () => api.change('r', 'member', MemberAction.promote, '"room-4"'),
    ).thenThrow(
      const RoomApiException(code: 'ROOM_REVISION_MISMATCH', statusCode: 412),
    );
    final controller = MemberController(api, 'r');
    await controller.load();
    expect(await controller.change(member, MemberAction.promote), isFalse);
    expect(controller.stale, isTrue);
    expect(controller.error, contains('Reload members'));
    expect(await controller.change(member, MemberAction.promote), isFalse);
    await controller.load();
    expect(controller.stale, isFalse);
    controller.dispose();
  });
  test(
    'access loss clears member data and protected widget content after sync',
    () async {
      final api = MockMemberApi();
      final widgets = RecordingWidgets();
      when(() => api.load('r')).thenAnswer((_) async => view());
      final controller = MemberController(api, 'r', widgets);
      await controller.load();
      when(() => api.load('r')).thenThrow(
        const RoomApiException(code: 'ROOM_UNAVAILABLE', statusCode: 404),
      );
      await controller.load();
      expect(controller.snapshot, isNull);
      expect(widgets.states, ['r:unavailable']);
      controller.dispose();
    },
  );
  test(
    'denied member action does not mistake room access for action authorization',
    () async {
      final api = MockMemberApi();
      final widgets = RecordingWidgets();
      when(() => api.load('r')).thenAnswer((_) async => view());
      when(
        () => api.change('r', 'member', MemberAction.remove, '"room-4"'),
      ).thenThrow(
        const RoomApiException(code: 'ROOM_ACTION_FORBIDDEN', statusCode: 403),
      );
      final controller = MemberController(api, 'r', widgets);
      await controller.load();
      expect(await controller.change(member, MemberAction.remove), isFalse);
      expect(controller.stale, isTrue);
      expect(widgets.states, isEmpty);
      controller.dispose();
    },
  );

  test(
    'late member response cannot restore protected data after account cleanup',
    () async {
      final api = MockMemberApi();
      final widgets = RecordingWidgets();
      final pending = Completer<MemberSnapshot>();
      when(() => api.load('r')).thenAnswer((_) => pending.future);
      final controller = MemberController(api, 'r', widgets);
      final load = controller.load();
      widgets.epoch++;
      pending.complete(view());
      await load;
      expect(controller.snapshot, isNull);
      controller.dispose();
    },
  );

  test(
    'offline reload disables actions and offers a clean retry message',
    () async {
      final api = MockMemberApi();
      when(() => api.load('r')).thenThrow(const RoomApiException());
      final controller = MemberController(api, 'r');
      await controller.load();
      expect(controller.error, contains('connection'));
      expect(controller.loading, isFalse);
      controller.dispose();
    },
  );
  for (final entry in {
    401: 'Sign in',
    403: 'current role',
    404: 'no longer',
    409: 'conflicts',
    412: 'Reload',
    422: 'Check',
    500: 'connection',
  }.entries) {
    test('safe mapping for HTTP ${entry.key}', () {
      expect(
        memberErrorMessage(
          RoomApiException(
            statusCode: entry.key,
            code: entry.key == 422 ? 'VALIDATION_FAILED' : null,
          ),
        ),
        contains(entry.value),
      );
    });
  }
  testWidgets('read-only member list shows safe names and no management menu', (
    tester,
  ) async {
    final controller = MemberController(MockMemberApi(), 'r')
      ..snapshot = view(RoomRole.member);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          memberControllerProvider.overrideWith((ref, id) => controller),
        ],
        child: const MaterialApp(home: MemberScreen(roomId: 'r')),
      ),
    );
    expect(find.text('Members · 3/25'), findsOneWidget);
    expect(find.text('Mira'), findsOneWidget);
    expect(find.byType(PopupMenuButton<MemberAction>), findsNothing);
  });
  testWidgets(
    'destructive remove requires confirmation and cancellation makes no request',
    (tester) async {
      final api = MockMemberApi();
      final controller = MemberController(api, 'r')..snapshot = view();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            memberControllerProvider.overrideWith((ref, id) => controller),
          ],
          child: const MaterialApp(home: MemberScreen(roomId: 'r')),
        ),
      );
      await tester.tap(find.byTooltip('Manage Mira'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Remove member'));
      await tester.pumpAndSettle();
      expect(find.text('Remove member?'), findsOneWidget);
      expect(find.textContaining('They can rejoin'), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      verifyNever(
        () => api.change('r', 'member', MemberAction.remove, '"room-4"'),
      );
    },
  );
  testWidgets('failed load shows reload state', (tester) async {
    final controller = MemberController(MockMemberApi(), 'r')
      ..error = 'Room unavailable.';
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          memberControllerProvider.overrideWith((ref, id) => controller),
        ],
        child: const MaterialApp(home: MemberScreen(roomId: 'r')),
      ),
    );
    expect(find.text('Room unavailable.'), findsOneWidget);
    expect(find.text('Reload members'), findsOneWidget);
  });
}
