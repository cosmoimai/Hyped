import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/app/router/app_router.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/data/room_repository.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_lifecycle.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_controllers.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_lifecycle_controller.dart';
import 'package:hyped/features/rooms/presentation/screens/room_lifecycle_screen.dart';
import 'package:mocktail/mocktail.dart';

class MockRooms extends Mock implements RoomRepository {}

class MockAuth extends Mock implements AuthRepository {}

class Widgets extends NoopRoomWidgetRepository {
  final cached = <Room>[];
  final marked = <String>[];
  int epoch = 0;
  @override
  int get sessionEpoch => epoch;
  @override
  Future<void> cacheRoom(Room room, {required int epoch}) async {
    cached.add(room);
  }

  @override
  Future<void> markRoom(
    String roomId,
    String state, {
    required int epoch,
  }) async {
    marked.add('$roomId:$state');
  }
}

final instant = DateTime.utc(2030);
Room sample({
  RoomStatus status = RoomStatus.active,
  RoomRole role = RoomRole.owner,
  int revision = 1,
  DateTime? now,
}) => Room(
  id: 'r',
  title: 'Trip',
  eventAt: DateTime.utc(2031),
  eventTimeZone: 'UTC',
  theme: const RoomTheme(
    kind: RoomThemeKind.preset,
    presetKey: 'soft-blue-01',
    overlayKey: 'dark-soft',
  ),
  status: status,
  role: role,
  memberCount: 2,
  revision: revision,
  createdAt: instant,
  updatedAt: instant,
  serverNow: now ?? instant,
  archivedAt: status == RoomStatus.archived ? instant : null,
  deleteAfter: status == RoomStatus.archived
      ? instant.add(const Duration(hours: 24))
      : null,
  etag: '"room-$revision"',
);
Map<String, dynamic> jsonRoom() => {
  'id': 'r',
  'title': 'Trip',
  'eventAt': '2031-01-01T00:00:00Z',
  'eventTimeZone': 'UTC',
  'theme': {
    'kind': 'PRESET',
    'presetKey': 'soft-blue-01',
    'overlayKey': 'dark-soft',
  },
  'status': 'ACTIVE',
  'role': 'OWNER',
  'memberCount': 2,
  'revision': 3,
  'createdAt': '2030-01-01T00:00:00Z',
  'updatedAt': '2030-01-01T00:00:00Z',
  'serverNow': '2030-01-01T00:00:00Z',
};

void main() {
  test(
    'members and co-hosts can leave; owner must transfer and cannot leave',
    () {
      expect(lifecycleActions(sample(role: RoomRole.member), instant), [
        RoomLifecycleAction.leave,
      ]);
      expect(lifecycleActions(sample(role: RoomRole.coHost), instant), [
        RoomLifecycleAction.leave,
      ]);
      expect(lifecycleActions(sample(), instant), [
        RoomLifecycleAction.archive,
      ]);
    },
  );
  test('restore closes and deletion opens at exact 24-hour equality', () {
    final room = sample(status: RoomStatus.archived);
    final deadline = room.deleteAfter!;
    expect(
      lifecycleActions(
        room,
        deadline.subtract(const Duration(microseconds: 1)),
      ),
      [RoomLifecycleAction.restore],
    );
    expect(lifecycleActions(room, deadline), [RoomLifecycleAction.delete]);
    expect(lifecycleActions(room, deadline.add(const Duration(hours: 1))), [
      RoomLifecycleAction.delete,
    ]);
    expect(
      lifecycleLabel(room, deadline),
      contains('eligible for permanent deletion'),
    );
  });
  test('archived members may leave but cannot restore or edit', () {
    final room = sample(status: RoomStatus.archived, role: RoomRole.coHost);
    expect(lifecycleActions(room, instant), [RoomLifecycleAction.leave]);
    expect(room.canEdit, isFalse);
    expect(
      lifecycleActions(sample(status: RoomStatus.deleting), instant),
      isEmpty,
    );
  });
  test(
    'API preserves archive contract and sends ETags to all lifecycle endpoints',
    () async {
      final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
      final adapter = DioAdapter(dio: dio);
      dio.httpClientAdapter = adapter;
      final api = RoomApi(dio);
      adapter.onDelete(
        '/rooms/r',
        (server) => server.reply(202, {
          'roomId': 'r',
          'status': 'ARCHIVED',
          'archivedAt': '2030-01-01T00:00:00Z',
          'deleteAfter': '2030-01-02T00:00:00Z',
          'revision': 2,
          'serverNow': '2030-01-01T00:00:00Z',
        }),
        headers: {'If-Match': '"room-1"'},
      );
      final archived = await api.lifecycle(
        sample(),
        RoomLifecycleAction.archive,
      );
      expect(archived!.status, RoomStatus.archived);
      expect(archived.etag, '"room-2"');
      expect(archived.title, 'Trip');
      adapter.onPost(
        '/rooms/r/restore',
        (server) => server.reply(200, jsonRoom()),
        headers: {'If-Match': '"room-2"'},
      );
      expect(
        (await api.lifecycle(archived, RoomLifecycleAction.restore))!.revision,
        3,
      );
      adapter.onPost(
        '/rooms/r/leave',
        (server) => server.reply(204, null),
        headers: {'If-Match': '"room-1"'},
      );
      expect(
        await api.lifecycle(
          sample(role: RoomRole.member),
          RoomLifecycleAction.leave,
        ),
        isNull,
      );
      adapter.onDelete(
        '/rooms/r/permanent',
        (server) => server.reply(204, null),
        headers: {'If-Match': '"room-2"'},
      );
      expect(await api.lifecycle(archived, RoomLifecycleAction.delete), isNull);
    },
  );
  test('room list includes active and archived rooms', () async {
    final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
    final adapter = DioAdapter(dio: dio);
    dio.httpClientAdapter = adapter;
    adapter.onGet(
      '/rooms',
      (server) => server.reply(200, {
        'items': [
          jsonRoom(),
          {
            ...jsonRoom(),
            'id': 'a',
            'status': 'ARCHIVED',
            'archivedAt': '2030-01-01T00:00:00Z',
            'deleteAfter': '2030-01-02T00:00:00Z',
          },
        ],
      }),
      queryParameters: {'limit': 50},
    );
    expect(await RoomRepository(RoomApi(dio)).listRooms(), hasLength(2));
  });
  test(
    'archived room is never featured as the next active countdown',
    () async {
      final repo = MockRooms();
      when(
        () => repo.listRooms(),
      ).thenAnswer((_) async => [sample(status: RoomStatus.archived)]);
      final controller = RoomListController(repo);
      await controller.load();
      expect(controller.nearestRoom, isNull);
      expect(controller.otherRooms.single.status, RoomStatus.archived);
      controller.dispose();
    },
  );
  test(
    'controller uses synchronized server time instead of device wall time',
    () async {
      final repo = MockRooms();
      when(() => repo.getRoom('r')).thenAnswer(
        (_) async =>
            sample(status: RoomStatus.archived, now: DateTime.utc(2030, 1, 2)),
      );
      final controller = RoomLifecycleController(repo, 'r');
      await controller.load();
      expect(controller.actions, [RoomLifecycleAction.delete]);
      controller.dispose();
    },
  );
  test(
    'archive and restore synchronize the corresponding widget states',
    () async {
      final repo = MockRooms();
      final widgets = Widgets();
      final active = sample();
      final archived = sample(status: RoomStatus.archived, revision: 2);
      when(() => repo.getRoom('r')).thenAnswer((_) async => active);
      when(
        () => repo.lifecycle(active, RoomLifecycleAction.archive),
      ).thenAnswer((_) async => archived);
      when(
        () => repo.lifecycle(archived, RoomLifecycleAction.restore),
      ).thenAnswer((_) async => sample(revision: 3));
      final controller = RoomLifecycleController(repo, 'r', widgets);
      await controller.load();
      expect(await controller.change(RoomLifecycleAction.archive), isTrue);
      expect(controller.actions, [RoomLifecycleAction.restore]);
      expect(widgets.cached.last.status, RoomStatus.archived);
      expect(await controller.change(RoomLifecycleAction.restore), isTrue);
      expect(widgets.cached.last.status, RoomStatus.active);
      controller.dispose();
    },
  );
  for (final action in [
    RoomLifecycleAction.leave,
    RoomLifecycleAction.delete,
  ]) {
    test(
      'successful ${action.name} clears private data and revokes widget mapping',
      () async {
        final repo = MockRooms();
        final widgets = Widgets();
        final room = action == RoomLifecycleAction.leave
            ? sample(role: RoomRole.member)
            : sample(
                status: RoomStatus.archived,
                now: DateTime.utc(2030, 1, 2),
              );
        when(() => repo.getRoom('r')).thenAnswer((_) async => room);
        when(() => repo.lifecycle(room, action)).thenAnswer((_) async => null);
        final controller = RoomLifecycleController(repo, 'r', widgets);
        await controller.load();
        expect(await controller.change(action), isTrue);
        expect(controller.removed, isTrue);
        expect(controller.room, isNull);
        expect(controller.actions, isEmpty);
        expect(widgets.marked, ['r:removed']);
        controller.dispose();
      },
    );
  }
  test('controller rejects owner leave and duplicate submissions', () async {
    final repo = MockRooms();
    final room = sample();
    final pending = Completer<Room?>();
    when(() => repo.getRoom('r')).thenAnswer((_) async => room);
    when(
      () => repo.lifecycle(room, RoomLifecycleAction.archive),
    ).thenAnswer((_) => pending.future);
    final controller = RoomLifecycleController(repo, 'r');
    await controller.load();
    expect(await controller.change(RoomLifecycleAction.leave), isFalse);
    final first = controller.change(RoomLifecycleAction.archive);
    expect(await controller.change(RoomLifecycleAction.archive), isFalse);
    pending.complete(sample(status: RoomStatus.archived));
    expect(await first, isTrue);
    controller.dispose();
  });
  test(
    'revision conflict disables actions until authoritative reload',
    () async {
      final repo = MockRooms();
      final room = sample();
      when(() => repo.getRoom('r')).thenAnswer((_) async => room);
      when(() => repo.lifecycle(room, RoomLifecycleAction.archive)).thenThrow(
        const RoomApiException(code: 'ROOM_REVISION_MISMATCH', statusCode: 412),
      );
      final controller = RoomLifecycleController(repo, 'r');
      await controller.load();
      expect(await controller.change(RoomLifecycleAction.archive), isFalse);
      expect(controller.actions, isEmpty);
      expect(controller.error, contains('Reload'));
      await controller.load();
      expect(controller.actions, [RoomLifecycleAction.archive]);
      controller.dispose();
    },
  );
  test(
    'removed or inaccessible room load clears protected widget snapshot',
    () async {
      final repo = MockRooms();
      final widgets = Widgets();
      when(() => repo.getRoom('r')).thenThrow(
        const RoomApiException(code: 'ROOM_UNAVAILABLE', statusCode: 404),
      );
      final controller = RoomLifecycleController(repo, 'r', widgets);
      await controller.load();
      expect(controller.room, isNull);
      expect(controller.error, contains('removed or is no longer accessible'));
      expect(widgets.marked, ['r:removed']);
      controller.dispose();
    },
  );
  test(
    'offline lifecycle failure preserves last synchronized widget data',
    () async {
      final repo = MockRooms();
      final widgets = Widgets();
      when(() => repo.getRoom('r')).thenThrow(const RoomApiException());
      final controller = RoomLifecycleController(repo, 'r', widgets);
      await controller.load();
      expect(widgets.marked, isEmpty);
      expect(controller.error, contains('connection'));
      controller.dispose();
    },
  );
  test(
    'late lifecycle response cannot repopulate widgets after account cleanup',
    () async {
      final repo = MockRooms();
      final widgets = Widgets();
      final pending = Completer<Room>();
      when(() => repo.getRoom('r')).thenAnswer((_) => pending.future);
      final controller = RoomLifecycleController(repo, 'r', widgets);
      final load = controller.load();
      widgets.epoch++;
      pending.complete(sample());
      await load;
      expect(controller.room, isNull);
      expect(widgets.cached, isEmpty);
      controller.dispose();
    },
  );
  for (final entry in {
    'OWNERSHIP_TRANSFER_REQUIRED': 'Transfer ownership',
    'ROOM_RECOVERY_EXPIRED': '24-hour',
    'ROOM_DELETE_NOT_ELIGIBLE': '24-hour',
    'OWNED_ROOM_LIMIT_REACHED': 'limit',
    'JOINED_ROOM_LIMIT_REACHED': 'limit',
  }.entries) {
    test('safe lifecycle error mapping for ${entry.key}', () {
      expect(
        lifecycleError(RoomApiException(code: entry.key)),
        contains(entry.value),
      );
    });
  }
  testWidgets(
    'owner sees transfer explanation and archive confirmation with cancellation',
    (tester) async {
      final repo = MockRooms();
      when(() => repo.getRoom('r')).thenAnswer((_) async => sample());
      final controller = RoomLifecycleController(repo, 'r');
      await controller.load();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            roomLifecycleControllerProvider.overrideWith(
              (ref, id) => controller,
            ),
          ],
          child: const MaterialApp(home: RoomLifecycleScreen(roomId: 'r')),
        ),
      );
      expect(find.text('Leave room'), findsNothing);
      expect(find.textContaining('Transfer ownership'), findsOneWidget);
      await tester.scrollUntilVisible(find.text('Archive room'), 200);
      await tester.tap(find.text('Archive room'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('Joins and active reminders will stop'),
        findsOneWidget,
      );
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      verifyNever(() => repo.lifecycle(sample(), RoomLifecycleAction.archive));
    },
  );
  testWidgets('eligible archived owner sees delete and cannot restore', (
    tester,
  ) async {
    final repo = MockRooms();
    when(() => repo.getRoom('r')).thenAnswer(
      (_) async =>
          sample(status: RoomStatus.archived, now: DateTime.utc(2030, 1, 2)),
    );
    final controller = RoomLifecycleController(repo, 'r');
    await controller.load();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          roomLifecycleControllerProvider.overrideWith((ref, id) => controller),
        ],
        child: const MaterialApp(home: RoomLifecycleScreen(roomId: 'r')),
      ),
    );
    await tester.scrollUntilVisible(find.text('Permanently delete room'), 200);
    expect(find.text('Permanently delete room'), findsOneWidget);
    expect(find.text('Restore room'), findsNothing);
  });
  testWidgets(
    'lifecycle route is reachable when authenticated and redirects after expiry',
    (tester) async {
      final authRepo = MockAuth();
      when(
        () => authRepo.restoreSession(any()),
      ).thenAnswer((_) async => SessionRestoreResult.authenticated);
      final signal = SessionInvalidationSignal();
      final auth = AuthController(authRepo, signal);
      await auth.initialize();
      final rooms = MockRooms();
      when(
        () => rooms.getRoom('r'),
      ).thenAnswer((_) async => sample(role: RoomRole.member));
      final controller = RoomLifecycleController(rooms, 'r');
      await controller.load();
      final router = createRouter(auth);
      addTearDown(router.dispose);
      addTearDown(signal.dispose);
      router.go('/rooms/r/lifecycle');
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authControllerProvider.overrideWith((ref) => auth),
            roomLifecycleControllerProvider.overrideWith(
              (ref, id) => controller,
            ),
          ],
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Leave room'), findsOneWidget);
      signal.invalidate();
      await tester.pumpAndSettle();
      expect(find.text('Welcome to Hyped!'), findsOneWidget);
    },
  );
}
