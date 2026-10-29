import 'package:hyped/features/android_widgets/domain/room_widget_data.dart';
import 'package:hyped/features/rooms/domain/room_lifecycle.dart';
import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/features/safety/data/safety_api.dart';
import 'package:hyped/features/safety/data/safety_invalidations.dart';
import 'package:hyped/features/safety/presentation/safety_providers.dart';
import 'package:hyped/features/safety/presentation/safety_sheets.dart';
import 'package:hyped/features/safety/presentation/blocked_accounts_screen.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/data/cache/room_cache_database.dart';
import 'package:hyped/features/rooms/data/cache/room_cache_session.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/rooms/domain/room_member.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'helpers/fakes.dart';
import 'room_widgets_test.dart' as fixtures;

class FakeSafetyApi extends SafetyApi {
  FakeSafetyApi() : super(Dio());
  final keys = <String>[];
  int reports = 0, blocksCount = 0, unblocks = 0;
  Future<void> Function() result = () async {};
  @override
  Future<void> report({
    required String roomId,
    required String subjectType,
    String? subjectUserId,
    required String reason,
    String? details,
    required String session,
    required String key,
  }) async {
    reports++;
    keys.add(key);
    await result();
  }

  @override
  Future<void> block(
    String target,
    String room,
    String session,
    String key,
  ) async {
    blocksCount++;
    keys.add(key);
    await result();
  }

  @override
  Future<void> unblock(String target, String session) async {
    unblocks++;
    await result();
  }
}

class SafetyAdapter implements HttpClientAdapter {
  SafetyAdapter(this.reply);
  final Future<Object> Function(RequestOptions) reply;
  final requests = <RequestOptions>[];
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? stream,
    Future<void>? cancel,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode(await reply(options)),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

class SafetyIdentity implements CacheIdentityStore {
  @override
  Future<({String account, String session})?> read() async => null;
  @override
  Future<void> write(String account, String session) async {}
  @override
  Future<void> clear() async {}
}

class SafetyWidgets extends NoopRoomWidgetRepository {
  final marked = <String>[];
  List<WidgetInstance> selected = [];
  @override
  Future<List<WidgetInstance>> instances() async => selected;
  @override
  Future<void> markRoom(String id, String state, {required int epoch}) async {
    marked.add('$id:$state');
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeSafetyApi api;
  late MemoryTokenStore store;
  late SafetyController controller;
  setUp(() {
    api = FakeSafetyApi();
    store = MemoryTokenStore(tokens());
    controller = SafetyController(api, store);
  });
  tearDown(() => controller.dispose());
  test(
    'report retries preserve a UUID key while a new payload gets another key',
    () async {
      api.result = () async => throw const RoomApiException(statusCode: 503);
      expect(
        await controller.submitReport('room', 'ROOM', null, 'SPAM', null),
        isFalse,
      );
      expect(
        await controller.submitReport('room', 'ROOM', null, 'SPAM', null),
        isFalse,
      );
      expect(api.keys[0], api.keys[1]);
      expect(RegExp(r'^[a-f0-9-]{36}$').hasMatch(api.keys[0]), isTrue);
      await controller.submitReport('room', 'ROOM', null, 'OTHER', null);
      expect(api.keys.last, isNot(api.keys.first));
      expect(controller.error, isNot(contains('503')));
    },
  );
  test(
    'successful report does not block and clears the completed request key',
    () async {
      expect(
        await controller.submitReport(
          'room',
          'ACCOUNT',
          'target',
          'HARASSMENT',
          'Details',
        ),
        isTrue,
      );
      expect(api.blocksCount, 0);
      await controller.submitReport(
        'room',
        'ACCOUNT',
        'target',
        'HARASSMENT',
        'Details',
      );
      expect(api.keys.first, isNot(api.keys.last));
    },
  );
  test(
    'block clears protected projections and unblock does not erase unrelated widgets',
    () async {
      int cleans = 0;
      final c = SafetyController(
        api,
        store,
        afterChange: (_) async {
          cleans++;
        },
      );
      addTearDown(c.dispose);
      expect(await c.block('target', 'room'), isTrue);
      expect(await c.unblock('target'), isTrue);
      expect(api.reports, 0);
      expect(cleans, 1);
    },
  );
  test('account switch and disposal fence late mutation results', () async {
    final pending = Completer<void>();
    api.result = () => pending.future;
    final request = controller.block('target', 'room');
    await Future<void>.delayed(Duration.zero);
    await store.clear();
    pending.complete();
    expect(await request, isFalse);
  });
  test(
    'a committed block remains successful if local refresh cleanup needs retry',
    () async {
      final c = SafetyController(
        api,
        store,
        afterChange: (_) async => throw StateError('private storage failure'),
      );
      addTearDown(c.dispose);
      expect(await c.block('target', 'room'), isTrue);
      expect(c.error, contains('finish synchronizing'));
      expect(c.error, isNot(contains('private storage')));
    },
  );
  test('overlapping safety mutations cannot duplicate submissions', () async {
    final pending = Completer<void>();
    api.result = () => pending.future;
    final first = controller.submitReport('room', 'ROOM', null, 'SPAM', null);
    expect(
      await controller.submitReport('room', 'ROOM', null, 'SPAM', null),
      isFalse,
    );
    pending.complete();
    expect(await first, isTrue);
    expect(api.reports, 1);
  });
  test('offline and report limits use clean messages', () async {
    api.result = () async =>
        throw const RoomApiException(code: 'OFFLINE_READ_ONLY');
    expect(await controller.block('target', 'room'), isFalse);
    expect(controller.error, contains('offline'));
    expect(
      safetyError(const RoomApiException(code: 'REPORT_LIMIT_REACHED')),
      contains('five reports'),
    );
    expect(
      safetyError(Exception('secret provider diagnostics')),
      isNot(contains('secret')),
    );
  });
  test(
    'moderation hold hides owner restore but preserves eligible permanent deletion',
    () {
      final now = DateTime.utc(2030, 1, 1);
      final room = fixtures.room().copyWith(
        status: RoomStatus.archived,
        moderationHold: true,
        deleteAfter: now.add(const Duration(hours: 24)),
      );
      expect(lifecycleActions(room, now), isEmpty);
      expect(lifecycleLabel(room, now), contains('under review'));
      expect(lifecycleActions(room, now.add(const Duration(hours: 24))), [
        RoomLifecycleAction.delete,
      ]);
      expect(lifecycleActions(room.copyWith(moderationHold: false), now), [
        RoomLifecycleAction.restore,
      ]);
    },
  );
  test('hidden profiles never expose membership actions', () {
    const hidden = RoomMember(
      userId: 'target',
      displayName: 'Blocked account',
      role: RoomRole.member,
      hiddenProfile: true,
    );
    const snapshot = MemberSnapshot([hidden], RoomRole.owner, true, '"room-1"');
    expect(memberActions(snapshot, hidden), isEmpty);
  });
  test(
    'API binds ownership to session, preserves idempotency and never sends a reporter ID',
    () async {
      final adapter = SafetyAdapter((_) async => {});
      final dio = Dio()..httpClientAdapter = adapter;
      final client = SafetyApi(dio);
      await client.report(
        roomId: 'room',
        subjectType: 'ACCOUNT',
        subjectUserId: 'target',
        reason: 'SPAM',
        session: 'session',
        key: 'request-key',
      );
      final request = adapter.requests.single;
      expect(request.path, '/reports');
      expect(request.data['subjectUserId'], 'target');
      expect(request.data.containsKey('userId'), isFalse);
      expect(request.data.containsKey('reporterUserId'), isFalse);
      expect(request.headers['Idempotency-Key'], 'request-key');
      expect(request.extra['expectedSessionId'], 'session');
    },
  );
  test(
    'blocked account pages are loaded without losing older unblock choices',
    () async {
      final adapter = SafetyAdapter(
        (request) async => request.queryParameters['after'] == null
            ? {
                'items': [
                  {'userId': 'first', 'displayName': 'First'},
                ],
                'nextCursor': 'next',
              }
            : {
                'items': [
                  {'userId': 'second', 'displayName': 'Second'},
                ],
                'nextCursor': null,
              },
      );
      final items = await SafetyApi(
        Dio()..httpClientAdapter = adapter,
      ).blocks('session');
      expect(items.map((a) => a.displayName), ['First', 'Second']);
      expect(adapter.requests.last.queryParameters['after'], 'next');
    },
  );
  test(
    'access invalidations erase cached room and widget before acknowledgment',
    () async {
      final database = RoomCacheDatabase(NativeDatabase.memory());
      addTearDown(database.close);
      final cache = RoomCacheSession(database, SafetyIdentity());
      await cache.start('account', 'session-one');
      final identity = cache.current!;
      final widgets = SafetyWidgets();
      await database.replaceList('account', [
        fixtures.room(),
      ], DateTime.now().toUtc());
      final adapter = SafetyAdapter((request) async {
        if (request.method == 'GET') {
          return {
            'items': [
              {'roomId': 'room-id', 'version': 'version'},
            ],
          };
        }
        expect(await database.readRoom('account', 'room-id'), isNull);
        expect(widgets.marked, ['room-id:unavailable']);
        return {};
      });
      await SafetyInvalidations(
        Dio()..httpClientAdapter = adapter,
        cache,
        store,
        widgets,
      ).synchronize(identity);
      expect(adapter.requests.last.path, '/me/room-invalidations/version');
      expect(cache.readGeneration, greaterThan(0));
    },
  );
  test(
    'local block clears every selected widget without removing independent mappings',
    () async {
      final database = RoomCacheDatabase(NativeDatabase.memory());
      addTearDown(database.close);
      final cache = RoomCacheSession(database, SafetyIdentity());
      await cache.start('account', 'session-one');
      await database.replaceList('account', [
        fixtures.room(),
      ], DateTime.now().toUtc());
      final widgets = SafetyWidgets()
        ..selected = [
          const WidgetInstance(id: 1, size: 'small', roomId: 'room-id'),
          const WidgetInstance(id: 2, size: 'medium', roomId: 'other-room'),
          const WidgetInstance(id: 3, size: 'small', roomId: 'room-id'),
        ];
      await clearSafetyProjections(cache, widgets, store, 'session-one');
      expect(await database.readList('account'), isNull);
      expect(widgets.marked, ['room-id:unavailable', 'other-room:unavailable']);
      expect(widgets.selected.length, 3);
    },
  );
  test(
    'failed invalidation acknowledgment leaves work pending without blocking reads',
    () async {
      final database = RoomCacheDatabase(NativeDatabase.memory());
      addTearDown(database.close);
      final cache = RoomCacheSession(database, SafetyIdentity());
      await cache.start('account', 'session-one');
      final widgets = SafetyWidgets();
      final adapter = SafetyAdapter((request) async {
        if (request.method == 'GET') {
          return {
            'items': [
              {'roomId': 'room-id', 'version': 'version'},
            ],
          };
        }
        throw DioException(
          requestOptions: request,
          type: DioExceptionType.badResponse,
          response: Response(requestOptions: request, statusCode: 403),
        );
      });
      await SafetyInvalidations(
        Dio()..httpClientAdapter = adapter,
        cache,
        store,
        widgets,
      ).synchronize(cache.current!);
      expect(widgets.marked, ['room-id:unavailable']);
      expect(adapter.requests.last.method, 'DELETE');
    },
  );
  test('late access invalidations cannot clear another account data', () async {
    final database = RoomCacheDatabase(NativeDatabase.memory());
    addTearDown(database.close);
    final cache = RoomCacheSession(database, SafetyIdentity());
    await cache.start('account', 'session-one');
    final identity = cache.current!;
    final widgets = SafetyWidgets();
    final reply = Completer<Object>();
    final adapter = SafetyAdapter((_) => reply.future);
    final sync = SafetyInvalidations(
      Dio()..httpClientAdapter = adapter,
      cache,
      store,
      widgets,
    ).synchronize(identity);
    await Future<void>.delayed(Duration.zero);
    await cache.start('other', 'other-session');
    await database.replaceList('other', [
      fixtures.room(),
    ], DateTime.now().toUtc());
    reply.complete({
      'items': [
        {'roomId': 'room-id', 'version': 'version'},
      ],
    });
    await expectLater(sync, throwsA(isA<RoomApiException>()));
    expect(await database.readRoom('other', 'room-id'), isNotNull);
    expect(widgets.marked, isEmpty);
  });
  test(
    'failed protected deletion disables cache reads and leaves server work unacknowledged',
    () async {
      final database = RoomCacheDatabase(NativeDatabase.memory());
      final cache = RoomCacheSession(database, SafetyIdentity());
      await cache.start('account', 'session-one');
      final identity = cache.current!;
      await database.close();
      await expectLater(
        cache.removeProtected(identity, 'room-id'),
        throwsA(anything),
      );
      bool read = false;
      await cache.run(identity, () async {
        read = true;
      });
      expect(read, isFalse);
    },
  );
  testWidgets(
    'report dialog requires a reason and supports cancellation without submitting',
    (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [safetyControllerProvider.overrideWith((_) => controller)],
          child: MaterialApp(
            home: Scaffold(
              body: Builder(
                builder: (context) => TextButton(
                  onPressed: () =>
                      showReport(context, roomId: 'room', subject: 'ROOM'),
                  child: const Text('Open report'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open report'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Send report'),
            )
            .onPressed,
        isNull,
      );
      expect(find.text('Details (optional)'), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(api.reports, 0);
    },
  );
  testWidgets(
    'blocked accounts show empty state and accessible return navigation',
    (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [blockedAccountsProvider.overrideWith((_) async => [])],
          child: const MaterialApp(home: BlockedAccountsScreen()),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('No blocked accounts.'), findsOneWidget);
      expect(find.byTooltip('Back'), findsOneWidget);
    },
  );
  testWidgets('unblock needs deliberate confirmation', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          blockedAccountsProvider.overrideWith(
            (_) async => [const BlockedAccount('target', 'Other person')],
          ),
          safetyControllerProvider.overrideWith((_) => controller),
        ],
        child: const MaterialApp(home: BlockedAccountsScreen()),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Unblock'));
    await tester.pumpAndSettle();
    expect(find.text('Unblock Other person?'), findsOneWidget);
    expect(api.unblocks, 0);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(api.unblocks, 0);
  });
}
