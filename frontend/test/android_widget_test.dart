import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:hyped/core/api/authentication_interceptor.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/android_widgets/data/widget_refresh_coordinator.dart';
import 'package:hyped/features/android_widgets/data/widget_token_store.dart';
import 'package:hyped/features/android_widgets/domain/room_widget_data.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/room_widget_controller.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/room_widget_providers.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/widget_navigation.dart';
import 'package:hyped/features/android_widgets/presentation/screens/room_widget_settings_screen.dart';
import 'package:hyped/features/authentication/data/auth_api.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/data/room_repository.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_commands.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_controllers.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:mocktail/mocktail.dart';
import 'helpers/fakes.dart';

class _MockRoomRepository extends Mock implements RoomRepository {}

class _MockAuthApi extends Mock implements AuthApi {}

class _MockIdentity extends Mock implements IdentityProvider {}

const _channel = MethodChannel('hyped/android_widgets');

/// Channel spy: Kotlin's persistence, pruning and sanitization are tested on JVM.
class _Bridge {
  final calls = <MethodCall>[];
  List<Map<String, Object?>> widgets = [
    {'appWidgetId': 41, 'size': 'small', 'roomId': null},
    {'appWidgetId': 42, 'size': 'medium', 'roomId': null},
  ];
  Future<Object?> handle(MethodCall call) async {
    calls.add(call);
    switch (call.method) {
      case 'listWidgets':
        return widgets;
      case 'selectRoom':
        final args = call.arguments as Map;
        final id = args['appWidgetId'];
        final room = args['room'] as Map;
        for (final widget in widgets) {
          if (widget['appWidgetId'] == id) widget['roomId'] = room['roomId'];
        }
      case 'clearSelection':
        for (final widget in widgets) {
          if (widget['appWidgetId'] == (call.arguments as Map)['appWidgetId']) {
            widget['roomId'] = null;
          }
        }
      case 'clearProtectedContent':
        for (final widget in widgets) {
          widget['roomId'] = null;
        }
    }
    return null;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late _Bridge bridge;
  late MethodChannelRoomWidgetRepository widgets;
  setUp(() async {
    bridge = _Bridge();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_channel, bridge.handle);
    widgets = MethodChannelRoomWidgetRepository();
    await widgets.startSession();
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_channel, null);
  });

  for (final state in {
    'ACCOUNT_DELETED': 410,
    'ACCOUNT_DELETION_PENDING': 403,
  }.entries) {
    test(
      '${state.key} response removes widgets and ends the session',
      () async {
        final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
        final store = MemoryTokenStore(tokens());
        var invalidated = false;
        final refresher = FakeRefresher(tokens('new'));
        dio.interceptors.add(
          AuthenticationInterceptor(
            dio,
            WidgetTokenStore(store, widgets),
            refresher,
            onAuthenticationFailed: () => invalidated = true,
          ),
        );
        final adapter = DioAdapter(dio: dio);
        dio.httpClientAdapter = adapter;
        adapter.onGet(
          '/private',
          (server) => server.reply(state.value, {'code': state.key}),
        );
        await expectLater(
          dio.get<dynamic>('/private'),
          throwsA(isA<DioException>()),
        );
        expect(bridge.calls.last.method, 'clearProtectedContent');
        expect(store.value, isNull);
        expect(invalidated, isTrue);
        expect(refresher.calls, 0);
      },
    );
  }

  testWidgets(
    'settings configure selected widget and explain update/offline behavior',
    (tester) async {
      final repository = _MockRoomRepository();
      when(
        repository.listRooms,
      ).thenAnswer((_) async => [room(id: 'first'), room(id: 'second')]);
      when(
        () => repository.getRoom('first'),
      ).thenAnswer((_) async => room(id: 'first'));
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            roomWidgetRepositoryProvider.overrideWithValue(widgets),
            roomRepositoryProvider.overrideWithValue(repository),
            roomListControllerProvider.overrideWith(
              (ref) => RoomListController(repository)..load(),
            ),
          ],
          child: const MaterialApp(
            home: RoomWidgetSettingsScreen(appWidgetId: 42),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Medium widget 42'), findsOneWidget);
      expect(
        find.textContaining('not continuously network-synchronized'),
        findsOneWidget,
      );
      await tester.tap(find.text('Goa trip').first);
      await tester.pumpAndSettle();
      expect(
        (bridge.calls
                .where((call) => call.method == 'selectRoom')
                .last
                .arguments
            as Map)['appWidgetId'],
        42,
      );
      expect(bridge.widgets.first['roomId'], isNull);
      expect(bridge.widgets.last['roomId'], 'first');
    },
  );

  test(
    'selection refreshes room and stores its successful server time',
    () async {
      final controller = RoomWidgetController(
        widgets,
        (id) async => room(id: id),
      );
      await controller.load();
      await controller.select(room());
      final payload =
          (bridge.calls
                      .where((call) => call.method == 'selectRoom')
                      .last
                      .arguments
                  as Map)['room']
              as Map;
      expect(payload['syncedAt'], room().serverNow.millisecondsSinceEpoch);
      controller.dispose();
    },
  );

  test(
    'failed selection refresh cannot repopulate unavailable room from stale list',
    () async {
      final controller = RoomWidgetController(widgets, (_) async {
        throw const RoomApiException(statusCode: 404);
      });
      await controller.load();
      await controller.select(room());
      expect(
        bridge.calls.where((call) => call.method == 'selectRoom'),
        isEmpty,
      );
      expect(controller.message, contains('Please retry'));
      controller.dispose();
    },
  );

  for (final boundary in {
    -1: 'Event started',
    0: 'Event started',
    1: 'Less than 1m',
    59999: 'Less than 1m',
    60000: '1m',
    3599999: '59m',
    3600000: '1h 0m',
    86399999: '23h 59m',
    86400000: '1d 0h',
  }.entries) {
    test('countdown boundary ${boundary.key} milliseconds', () {
      final now = DateTime.utc(2030);
      final data = RoomWidgetViewData.fromData(
        RoomWidgetData.fromRoom(
          room(eventAt: now.add(Duration(milliseconds: boundary.key))),
        ),
        now,
      );
      expect(data.countdownText, boundary.value);
      expect(data.completed, boundary.key <= 0);
    });
  }

  test(
    'payload contains only safe fields, UTC instant and allowlisted theme',
    () {
      final data = RoomWidgetData.fromRoom(
        room(preset: 'https://private/image'),
        syncedAt: DateTime.utc(2030),
      );
      expect(data.toJson(), {
        'roomId': 'room-id',
        'title': 'Goa trip',
        'eventAt': DateTime.utc(2030, 12, 20, 4, 30).millisecondsSinceEpoch,
        'presetKey': 'soft-blue-01',
        'syncedAt': DateTime.utc(2030).millisecondsSinceEpoch,
        'state': 'active',
      });
    },
  );

  test('archived and deleting rooms carry explicit safe states', () {
    expect(
      RoomWidgetData.fromRoom(room(status: RoomStatus.archived)).state,
      'archived',
    );
    final removed = RoomWidgetData.fromRoom(room(status: RoomStatus.deleting));
    expect(removed.state, 'removed');
    expect(removed.title, isEmpty);
    expect(removed.eventAt.millisecondsSinceEpoch, 0);
  });

  test(
    'settings select different rooms for independent installed widgets',
    () async {
      final controller = RoomWidgetController(widgets);
      addTearDown(controller.dispose);
      await controller.load(preferredId: 42);
      expect(controller.selectedId, 42);
      await controller.select(room(id: 'second-room'));
      controller.chooseWidget(41);
      await controller.select(room(id: 'first-room'));
      expect((await widgets.instances()).map((widget) => widget.roomId), [
        'first-room',
        'second-room',
      ]);
      await controller.clear();
      expect((await widgets.instances()).map((widget) => widget.roomId), [
        null,
        'second-room',
      ]);
      controller.chooseWidget(999);
      expect(controller.selectedId, 41);
    },
  );

  test(
    'stale preferred ID falls back to installed widget and empty host is supported',
    () async {
      final controller = RoomWidgetController(widgets);
      await controller.load(preferredId: 999);
      expect(controller.selectedId, 41);
      bridge.widgets = [];
      await controller.load();
      expect(controller.selectedId, isNull);
      await controller.select(room());
      expect(
        bridge.calls.where((call) => call.method == 'selectRoom'),
        isEmpty,
      );
      controller.dispose();
    },
  );

  test(
    'logout fences in-flight room data even after another account starts',
    () async {
      final oldEpoch = widgets.sessionEpoch;
      await widgets.clearProtectedContent();
      await widgets.startSession();
      await widgets.cacheRoom(room(), epoch: oldEpoch);
      await widgets.cacheRooms([room()], epoch: oldEpoch);
      await widgets.markRoom('room-id', 'removed', epoch: oldEpoch);
      await expectLater(
        widgets.selectRoom(41, room(), epoch: oldEpoch),
        throwsStateError,
      );
      expect(bridge.calls.map((call) => call.method), [
        'clearProtectedContent',
      ]);
      await widgets.cacheRoom(room(), epoch: widgets.sessionEpoch);
      expect(bridge.calls.last.method, 'cacheRoom');
    },
  );

  test('token deletion clears widgets and credentials', () async {
    final tokensStore = MemoryTokenStore(tokens());
    final store = WidgetTokenStore(tokensStore, widgets);
    await widgets.selectRoom(41, room(), epoch: widgets.sessionEpoch);
    await store.clear();
    expect(tokensStore.value, isNull);
    expect(
      (await widgets.instances()).every((widget) => widget.roomId == null),
      isTrue,
    );
    expect(
      bridge.calls.map((call) => call.method),
      contains('clearProtectedContent'),
    );
  });

  test('cleanup still deletes credentials when native bridge fails', () async {
    final tokensStore = MemoryTokenStore(tokens());
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_channel, (call) async {
          throw PlatformException(code: 'failed');
        });
    await expectLater(
      WidgetTokenStore(tokensStore, widgets).clear(),
      throwsA(isA<PlatformException>()),
    );
    expect(tokensStore.value, isNull);
  });

  test(
    'real logout clears widget content even when revocation fails',
    () async {
      final api = _MockAuthApi();
      when(api.logout).thenThrow(StateError('offline'));
      final store = MemoryTokenStore(tokens());
      await widgets.selectRoom(42, room(), epoch: widgets.sessionEpoch);
      final auth = AuthRepository(
        api,
        WidgetTokenStore(store, widgets),
        FakeDeviceMetadataProvider(),
        null,
        widgets,
      );
      await expectLater(auth.logout(), throwsStateError);
      expect(store.value, isNull);
      expect((await widgets.instances()).last.roomId, isNull);
    },
  );

  test(
    'account change clears previous selections before sign-in proof is requested',
    () async {
      final api = _MockAuthApi();
      final identity = _MockIdentity();
      await widgets.selectRoom(41, room(), epoch: widgets.sessionEpoch);
      when(identity.firebaseIdToken).thenAnswer((_) async {
        expect(bridge.calls.last.method, 'clearProtectedContent');
        return 'firebase-proof';
      });
      when(
        () => api.exchange(
          firebaseIdToken: any(named: 'firebaseIdToken'),
          installationId: any(named: 'installationId'),
          deviceName: any(named: 'deviceName'),
          appVersion: any(named: 'appVersion'),
        ),
      ).thenAnswer((_) async => exchangeResponse('new-account'));
      final auth = AuthRepository(
        api,
        MemoryTokenStore(),
        FakeDeviceMetadataProvider(),
        null,
        widgets,
      );
      final oldEpoch = widgets.sessionEpoch;
      await auth.signIn(identity);
      expect(widgets.sessionEpoch, greaterThan(oldEpoch));
      expect((await widgets.instances()).first.roomId, isNull);
    },
  );

  test(
    'successful list and detail refreshes coordinate safe payloads',
    () async {
      final repository = _MockRoomRepository();
      when(repository.listRooms).thenAnswer((_) async => [room()]);
      when(() => repository.getRoom('room-id')).thenAnswer((_) async => room());
      await RoomListController(repository, widgets).load();
      await RoomDetailController(repository, 'room-id', widgets).load();
      expect(bridge.calls.map((call) => call.method), [
        'cacheRooms',
        'listWidgets',
        'cacheRoom',
      ]);
      expect(
        (bridge.calls.last.arguments as Map).keys,
        unorderedEquals([
          'roomId',
          'title',
          'eventAt',
          'presetKey',
          'syncedAt',
          'state',
        ]),
      );
    },
  );

  test(
    'successful room edit refreshes widget; widget failure does not fail save',
    () async {
      final repository = _MockRoomRepository();
      final current = room();
      final draft = RoomDraft(
        title: current.title,
        eventDate: current.eventAt,
        eventTime: (hour: 10, minute: 0),
        eventTimeZone: 'UTC',
        theme: current.theme,
      );
      when(
        () => repository.getRoom('room-id'),
      ).thenAnswer((_) async => current);
      when(
        () => repository.updateRoom(current, draft),
      ).thenAnswer((_) async => current);
      final detail = RoomDetailController(repository, 'room-id', widgets);
      await detail.load();
      expect(await detail.save(draft), isTrue);
      expect(
        bridge.calls.where((call) => call.method == 'cacheRoom'),
        hasLength(2),
      );
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(_channel, (_) async {
            throw PlatformException(code: 'failed');
          });
      expect(await detail.save(draft), isTrue);
      expect(detail.error, isNull);
    },
  );

  for (final status in [403, 404, 410]) {
    test(
      'missing selected room is checked by detail and maps $status safely',
      () async {
        bridge.widgets.first['roomId'] = 'missing';
        final fetches = <String>[];
        await WidgetRefreshCoordinator(widgets).afterList([], (id) async {
          fetches.add(id);
          throw RoomApiException(statusCode: status);
        }, widgets.sessionEpoch);
        expect(fetches, ['missing']);
        expect(bridge.calls.last.method, 'markRoom');
        expect(bridge.calls.last.arguments, {
          'roomId': 'missing',
          'state': status == 403 ? 'unavailable' : 'removed',
        });
      },
    );
  }

  test(
    'archived selected room absent from active list is refreshed by detail',
    () async {
      bridge.widgets.first['roomId'] = 'archived';
      await WidgetRefreshCoordinator(widgets).afterList(
        [],
        (id) async => room(id: id, status: RoomStatus.archived),
        widgets.sessionEpoch,
      );
      expect(bridge.calls.last.method, 'cacheRoom');
      expect((bridge.calls.last.arguments as Map)['state'], 'archived');
    },
  );

  test(
    'offline list/detail and backend errors never overwrite cached display',
    () async {
      final repository = _MockRoomRepository();
      when(repository.listRooms).thenThrow(const RoomApiException());
      when(
        () => repository.getRoom('room-id'),
      ).thenThrow(const RoomApiException(statusCode: 500));
      await RoomListController(repository, widgets).load();
      await RoomDetailController(repository, 'room-id', widgets).load();
      expect(bridge.calls, isEmpty);
      bridge.widgets.first['roomId'] = 'room-id';
      await WidgetRefreshCoordinator(widgets).afterList([], (_) async {
        throw const RoomApiException();
      }, widgets.sessionEpoch);
      expect(bridge.calls.map((call) => call.method), [
        'cacheRooms',
        'listWidgets',
      ]);
    },
  );

  test('late list load from previous account cannot refresh widgets', () async {
    final repository = _MockRoomRepository();
    final result = Completer<List<Room>>();
    when(repository.listRooms).thenAnswer((_) => result.future);
    final loading = RoomListController(repository, widgets).load();
    await widgets.clearProtectedContent();
    await widgets.startSession();
    result.complete([room()]);
    await loading;
    expect(bridge.calls.map((call) => call.method), ['clearProtectedContent']);
  });

  test(
    'widget launch stays pending through authentication and is consumed once',
    () {
      final navigation = WidgetNavigation();
      navigation.receive('/rooms/room-id');
      expect(navigation.takeIfAuthenticated(false), isNull);
      expect(navigation.takeIfAuthenticated(true), '/rooms/room-id');
      expect(navigation.takeIfAuthenticated(true), isNull);
      navigation.receive('/widgets/room?widgetId=42');
      expect(navigation.takeIfAuthenticated(true), '/widgets/room?widgetId=42');
      navigation.receive('/sign-in?token=private');
      expect(navigation.pending, isNull);
    },
  );
}

Room room({
  String id = 'room-id',
  DateTime? eventAt,
  RoomStatus status = RoomStatus.active,
  String preset = 'soft-blue-01',
}) => Room(
  id: id,
  title: 'Goa trip',
  eventAt: eventAt ?? DateTime.utc(2030, 12, 20, 4, 30),
  eventTimeZone: 'Asia/Kolkata',
  location: 'Private location',
  description: 'Private description',
  theme: RoomTheme(
    kind: RoomThemeKind.preset,
    presetKey: preset,
    overlayKey: 'dark-soft',
  ),
  status: status,
  role: RoomRole.member,
  memberCount: 8,
  revision: 4,
  createdAt: DateTime.utc(2026, 9, 9, 10),
  updatedAt: DateTime.utc(2026, 9, 9, 10, 30),
  serverNow: DateTime.utc(2026, 9, 9, 11),
);
