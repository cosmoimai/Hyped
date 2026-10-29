import 'package:hyped/core/api/authentication_interceptor.dart';
import 'dart:async';
import 'dart:io';
import 'package:dio/dio.dart';
import 'package:drift/native.dart';
import 'package:drift/drift.dart' show driftRuntimeOptions;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/app/router/app_router.dart';
import 'package:hyped/core/offline/offline_state.dart';
import 'package:hyped/features/authentication/data/auth_api.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/android_widgets/data/widget_refresh_coordinator.dart';
import 'package:hyped/features/android_widgets/domain/room_widget_data.dart';
import 'package:hyped/features/policy/presentation/policy_controller.dart';
import 'package:hyped/features/rooms/data/cache/cache_token_store.dart';
import 'package:hyped/features/rooms/data/cache/cache_invalidation_interceptor.dart';
import 'package:hyped/features/rooms/data/cache/room_cache_database.dart';
import 'package:hyped/features/rooms/data/cache/room_cache_session.dart';
import 'package:hyped/features/rooms/data/cache/cached_room_repository.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/data/member_api.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_member.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_controllers.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/rooms/presentation/controllers/member_controller.dart';
import 'package:hyped/features/rooms/presentation/screens/room_detail_screen.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_formatters.dart';
import 'package:hyped/features/invitations/data/invitation_api.dart';
import 'package:mocktail/mocktail.dart';
import 'helpers/fakes.dart';
import 'room_controllers_test.dart' as fixtures;

class MemoryCacheIdentity implements CacheIdentityStore {
  ({String account, String session})? value;
  @override
  Future<({String account, String session})?> read() async => value;
  @override
  Future<void> write(String account, String session) async {
    value = (account: account, session: session);
  }

  @override
  Future<void> clear() async {
    value = null;
  }
}

class MockCacheApi extends Mock implements RoomApi {}

class MockBrokenCache extends Mock implements RoomCacheDatabase {}

class MockCacheAuthApi extends Mock implements AuthApi {}

class MockCachedAuthRepository extends Mock implements AuthRepository {}

class MockCachedPolicy extends Mock implements PolicyController {}

class RecordingCacheWidgets extends NoopRoomWidgetRepository {
  final details = <Room>[];
  final lists = <List<Room>>[];
  final marks = <String>[];
  @override
  Future<void> cacheRoom(Room room, {required int epoch}) async {
    details.add(room);
  }

  @override
  Future<void> cacheRooms(List<Room> rooms, {required int epoch}) async {
    lists.add(rooms);
  }

  @override
  Future<void> markRoom(
    String roomId,
    String state, {
    required int epoch,
  }) async {
    marks.add('$roomId:$state');
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  // These tests deliberately use independent in-memory executors and a separate disk fixture.
  setUpAll(() {
    driftRuntimeOptions.dontWarnAboutMultipleDatabases = true;
  });
  tearDownAll(() {
    driftRuntimeOptions.dontWarnAboutMultipleDatabases = false;
  });
  late RoomCacheDatabase database;
  late MemoryCacheIdentity identities;
  late RoomCacheSession scope;
  late MemoryTokenStore tokensStore;
  late MockCacheApi api;
  late CachedRoomRepository repository;
  final sync = DateTime.utc(2030, 1, 1, 10);
  setUp(() async {
    database = RoomCacheDatabase(NativeDatabase.memory());
    identities = MemoryCacheIdentity();
    scope = RoomCacheSession(database, identities);
    tokensStore = MemoryTokenStore(tokens());
    api = MockCacheApi();
    repository = CachedRoomRepository(
      api,
      scope,
      tokensStore,
      clock: () => sync,
    );
    await scope.start('account-a', 'session-one');
    registerFallbackValue(fixtures.draft());
    registerFallbackValue(fixtures.room());
  });
  tearDown(() async {
    await database.close();
  });
  Future<void> seed([String title = 'Cached trip']) => database.replaceList(
    'account-a',
    [fixtures.room(title: title)],
    sync.subtract(const Duration(hours: 1)),
  );

  test(
    'photo references remain account partitioned and never store delivery URLs',
    () async {
      const id = '019b1f2b-d5e2-7d46-9165-a37ed59d6080';
      final room = fixtures.room().copyWith(
        theme: const RoomTheme(
          kind: RoomThemeKind.uploadedImage,
          presetKey: 'soft-blue-01',
          overlayKey: 'image-dark-40',
          mediaAssetId: id,
        ),
      );
      await database.put('account-a', room, sync);
      expect(
        (await database.readRoom('account-a', room.id))?.theme.mediaAssetId,
        id,
      );
      expect(await database.readRoom('account-b', room.id), isNull);
      final columns = await database
          .customSelect('PRAGMA table_info(cached_rooms)')
          .get();
      expect(
        columns.any((row) => row.read<String>('name').contains('url')),
        isFalse,
      );
      await database.erase();
      expect(await database.readRoom('account-a', room.id), isNull);
    },
  );

  test(
    'explicit schema contains no credentials, invite data or raw response blob',
    () async {
      final columns = await database
          .customSelect('PRAGMA table_info(cached_rooms)')
          .get();
      final names = columns.map((row) => row.read<String>('name')).toSet();
      expect(
        names,
        containsAll([
          'account_id',
          'room_id',
          'title',
          'event_at',
          'synced_at',
        ]),
      );
      expect(
        names.any(
          (name) =>
              RegExp('token|invite|code|session|payload|json').hasMatch(name),
        ),
        isFalse,
      );
      expect(database.schemaVersion, 2);
    },
  );
  test(
    'allowlisted projection round trips with UTC instant and original sync time',
    () async {
      await seed();
      final room = await repository.cachedDetail('room-id');
      expect(room!.title, 'Cached trip');
      expect(room.isCached, isTrue);
      expect(room.eventAt.isUtc, isTrue);
      expect(room.lastSyncedAt, sync.subtract(const Duration(hours: 1)));
      expect(room.etag, isNull);
    },
  );
  test('SQLite partitions identical room IDs by account', () async {
    await database.put('account-a', fixtures.room(title: 'A private'), sync);
    await database.put('account-b', fixtures.room(title: 'B private'), sync);
    expect(
      (await database.readRoom('account-a', 'room-id'))!.title,
      'A private',
    );
    expect(
      (await database.readRoom('account-b', 'room-id'))!.title,
      'B private',
    );
    expect(await database.readRoom('account-c', 'room-id'), isNull);
  });
  test(
    'changing account erases old protected rows and fences old work',
    () async {
      await seed();
      final old = scope.current!;
      await scope.start('account-b', 'session-two');
      expect(scope.matches(old), isFalse);
      expect(await database.readRoom('account-a', 'room-id'), isNull);
      expect(
        await scope.run<bool>(old, () async {
          await database.put('account-a', fixtures.room(), sync);
          return true;
        }),
        isNull,
      );
    },
  );
  test('a new session for the same account clears old cache', () async {
    await seed();
    await scope.start('account-a', 'new-session');
    expect(await database.readList('account-a'), isNull);
  });
  test(
    'cold process restore uses persisted secure identity without a network lookup',
    () async {
      await seed();
      final restored = RoomCacheSession(database, identities);
      final cold = CachedRoomRepository(api, restored, tokensStore);
      expect((await cold.cachedList())!.rooms.single.title, 'Cached trip');
      verifyNever(() => api.accountId(any()));
    },
  );
  test('cache survives a real SQLite close and reopen', () async {
    final directory = await Directory.systemTemp.createTemp('hyped-cache-test');
    final file = File('${directory.path}/cache.sqlite');
    var disk = RoomCacheDatabase(NativeDatabase(file));
    try {
      await disk.replaceList('account-a', [fixtures.room()], sync);
      await disk.close();
      disk = RoomCacheDatabase(NativeDatabase(file));
      expect((await disk.readList('account-a'))!.rooms.single.id, 'room-id');
    } finally {
      await disk.close();
      await directory.delete(recursive: true);
    }
  });
  test('cold list displays cached data before the server response', () async {
    await seed();
    final response = Completer<List<Room>>();
    when(
      () => api.listRooms(limit: 50, sessionId: 'session-one'),
    ).thenAnswer((_) => response.future);
    final controller = RoomListController(repository);
    final loaded = controller.load();
    await Future<void>.delayed(const Duration(milliseconds: 20));
    expect(controller.loading, isTrue);
    expect(controller.rooms.single.title, 'Cached trip');
    expect(controller.usingCache, isTrue);
    expect(controller.lastSync, sync.subtract(const Duration(hours: 1)));
    response.complete([fixtures.room(title: 'Authoritative trip')]);
    await loaded;
    expect(controller.usingCache, isFalse);
    expect(controller.rooms.single.title, 'Authoritative trip');
    expect(
      (await repository.cachedDetail('room-id'))!.title,
      'Authoritative trip',
    );
    expect(controller.lastSync, sync);
    controller.dispose();
  });
  test(
    'empty authoritative list removes stale rooms and retains empty-list sync metadata',
    () async {
      await seed();
      when(
        () => api.listRooms(limit: 50, sessionId: 'session-one'),
      ).thenAnswer((_) async => []);
      await repository.listRooms();
      expect(await repository.cachedDetail('room-id'), isNull);
      expect((await repository.cachedList())!.rooms, isEmpty);
      expect((await repository.cachedList())!.syncedAt, sync);
    },
  );
  test('archived server detail invalidates the active snapshot', () async {
    await seed();
    when(() => api.getRoom('room-id', 'session-one')).thenAnswer(
      (_) async => fixtures.room().copyWith(status: RoomStatus.archived),
    );
    expect((await repository.getRoom('room-id')).status, RoomStatus.archived);
    expect(await repository.cachedDetail('room-id'), isNull);
  });
  for (final status in [403, 404, 410]) {
    test(
      'server access loss $status removes cached detail and protected widget state',
      () async {
        await seed();
        when(
          () => api.getRoom('room-id', 'session-one'),
        ).thenThrow(RoomApiException(statusCode: status));
        final widgets = RecordingCacheWidgets();
        final controller = RoomDetailController(repository, 'room-id', widgets);
        await controller.load();
        expect(controller.room, isNull);
        expect(await repository.cachedDetail('room-id'), isNull);
        expect(widgets.marks, [
          'room-id:${status == 403 ? 'unavailable' : 'removed'}',
        ]);
        controller.dispose();
      },
    );
  }
  test(
    'late active detail cannot resurrect a room after access-loss invalidation',
    () async {
      await seed();
      final response = Completer<Room>();
      when(
        () => api.getRoom('room-id', 'session-one'),
      ).thenAnswer((_) => response.future);
      final old = repository.getRoom('room-id');
      await Future<void>.delayed(const Duration(milliseconds: 20));
      scope.invalidateReads(scope.current!);
      await database.remove('account-a', 'room-id');
      response.complete(fixtures.room(title: 'Late active snapshot'));
      await expectLater(
        old,
        throwsA(
          isA<RoomApiException>().having(
            (error) => error.code,
            'code',
            'CACHE_CHANGED',
          ),
        ),
      );
      expect(await repository.cachedDetail('room-id'), isNull);
    },
  );
  test(
    'late list cannot resurrect a room after a newer archived response',
    () async {
      await seed();
      final response = Completer<List<Room>>();
      when(
        () => api.listRooms(limit: 50, sessionId: 'session-one'),
      ).thenAnswer((_) => response.future);
      when(() => api.getRoom('room-id', 'session-one')).thenAnswer(
        (_) async => fixtures.room().copyWith(status: RoomStatus.archived),
      );
      final old = repository.listRooms();
      await Future<void>.delayed(const Duration(milliseconds: 20));
      await repository.getRoom('room-id');
      response.complete([fixtures.room()]);
      await expectLater(
        old,
        throwsA(
          isA<RoomApiException>().having(
            (error) => error.code,
            'code',
            'CACHE_CHANGED',
          ),
        ),
      );
      expect(await repository.cachedDetail('room-id'), isNull);
    },
  );
  test('late detail cannot repopulate an authoritative empty list', () async {
    await seed();
    final response = Completer<Room>();
    when(
      () => api.getRoom('room-id', 'session-one'),
    ).thenAnswer((_) => response.future);
    when(
      () => api.listRooms(limit: 50, sessionId: 'session-one'),
    ).thenAnswer((_) async => []);
    final old = repository.getRoom('room-id');
    await Future<void>.delayed(const Duration(milliseconds: 20));
    await repository.listRooms();
    response.complete(fixtures.room());
    await expectLater(
      old,
      throwsA(
        isA<RoomApiException>().having(
          (error) => error.code,
          'code',
          'CACHE_CHANGED',
        ),
      ),
    );
    expect((await repository.cachedList())!.rooms, isEmpty);
  });
  test('malformed row is discarded without exposing its diagnostics', () async {
    await seed();
    await database.customStatement(
      "UPDATE cached_rooms SET role='PRIVATE_BAD_VALUE'",
    );
    expect(await repository.cachedDetail('room-id'), isNull);
    expect(
      (await database
              .customSelect('SELECT count(*) AS count FROM cached_rooms')
              .getSingle())
          .read<int>('count'),
      0,
    );
  });
  test(
    'schema mismatch rebuilds derived cache without copying unknown fields',
    () async {
      final legacy = RoomCacheDatabase(
        NativeDatabase.memory(
          setup: (db) {
            db.execute('CREATE TABLE cached_rooms (secret TEXT)');
            db.execute("INSERT INTO cached_rooms VALUES ('never-copy-this')");
            db.execute('CREATE TABLE cache_lists (old_column TEXT)');
            db.execute('PRAGMA user_version = 99');
          },
        ),
      );
      try {
        expect(await legacy.readList('account-a'), isNull);
        await legacy.put('account-a', fixtures.room(), sync);
        expect(
          (await legacy.readRoom('account-a', 'room-id'))!.title,
          fixtures.room().title,
        );
        final columns = await legacy
            .customSelect('PRAGMA table_info(cached_rooms)')
            .get();
        expect(
          columns.map((row) => row.read<String>('name')),
          isNot(contains('secret')),
        );
      } finally {
        await legacy.close();
      }
    },
  );
  test(
    'broken cache storage cannot prevent a successful server detail',
    () async {
      await database.customStatement('DROP TABLE cached_rooms');
      when(() => api.getRoom('room-id', 'session-one')).thenAnswer(
        (_) async => fixtures.room(title: 'Live despite disk error'),
      );
      expect(
        (await repository.getRoom('room-id')).title,
        'Live despite disk error',
      );
    },
  );
  test(
    'offline errors retain cached detail and do not advance last sync or update widgets',
    () async {
      await seed();
      when(
        () => api.getRoom('room-id', 'session-one'),
      ).thenThrow(const RoomApiException(code: 'OFFLINE_READ_ONLY'));
      final widgets = RecordingCacheWidgets();
      final controller = RoomDetailController(repository, 'room-id', widgets);
      await controller.load();
      expect(controller.room!.title, 'Cached trip');
      expect(controller.readOnly, isTrue);
      expect(controller.error, offlineReadOnlyMessage);
      expect(widgets.details, isEmpty);
      expect(
        controller.room!.lastSyncedAt,
        sync.subtract(const Duration(hours: 1)),
      );
      expect(await controller.save(fixtures.draft()), isFalse);
      verifyNever(
        () => api.updateRoom(
          roomId: any(named: 'roomId'),
          etag: any(named: 'etag'),
          draft: any(named: 'draft'),
        ),
      );
      controller.dispose();
    },
  );
  test(
    'server detail refresh alone updates widgets and last successful sync',
    () async {
      await seed();
      final response = Completer<Room>();
      when(
        () => api.getRoom('room-id', 'session-one'),
      ).thenAnswer((_) => response.future);
      final widgets = RecordingCacheWidgets();
      final controller = RoomDetailController(repository, 'room-id', widgets);
      final load = controller.load();
      await Future<void>.delayed(const Duration(milliseconds: 20));
      expect(widgets.details, isEmpty);
      expect(controller.room!.isCached, isTrue);
      response.complete(fixtures.room(title: 'Fresh'));
      await load;
      expect(widgets.details.single.title, 'Fresh');
      expect(controller.room!.lastSyncedAt, sync);
      expect(RoomWidgetData.fromRoom(widgets.details.single).syncedAt, sync);
      controller.dispose();
    },
  );
  test('widget coordinator refuses cached snapshots', () async {
    final widgets = RecordingCacheWidgets();
    final cached = fixtures.room().copyWith(isCached: true);
    final coordinator = WidgetRefreshCoordinator(widgets);
    await coordinator.afterDetail(cached, 0);
    await coordinator.afterList([cached], (_) async => cached, 0);
    expect(widgets.details, isEmpty);
    expect(widgets.lists, isEmpty);
  });
  test(
    'all token deletion paths clear cache and secure ownership metadata',
    () async {
      await seed();
      final decorated = CacheTokenStore(tokensStore, scope);
      await decorated.clear();
      expect(tokensStore.value, isNull);
      expect(identities.value, isNull);
      expect(await database.readList('account-a'), isNull);
      expect(scope.current, isNull);
    },
  );
  test('failed backend logout still clears cache and credentials', () async {
    await seed();
    final logoutApi = MockCacheAuthApi();
    when(logoutApi.logout).thenThrow(Exception('offline'));
    final auth = AuthRepository(
      logoutApi,
      CacheTokenStore(tokensStore, scope),
      FakeDeviceMetadataProvider(),
      null,
      null,
      null,
      null,
      scope,
    );
    await expectLater(auth.logout(), throwsException);
    expect(tokensStore.value, isNull);
    expect(await database.readList('account-a'), isNull);
  });
  test(
    'authentication expiry clears cached rooms through the token-store decorator',
    () async {
      await seed();
      final auth = AuthRepository(
        MockCacheAuthApi(),
        CacheTokenStore(tokensStore, scope),
        FakeDeviceMetadataProvider(),
        null,
        null,
        null,
        null,
        scope,
      );
      expect(
        await auth.restoreSession(DateTime.utc(2040)),
        SessionRestoreResult.refreshFailed,
      );
      expect(await database.readList('account-a'), isNull);
      expect(tokensStore.value, isNull);
    },
  );
  test('late response cannot repopulate cache after logout', () async {
    await seed();
    final response = Completer<Room>();
    when(
      () => api.getRoom('room-id', 'session-one'),
    ).thenAnswer((_) => response.future);
    final request = repository.getRoom('room-id');
    await Future<void>.delayed(const Duration(milliseconds: 20));
    await scope
        .clear(); // Tokens can still exist while remote logout is in flight.
    response.complete(fixtures.room(title: 'Old account late response'));
    await expectLater(request, throwsA(isA<RoomApiException>()));
    expect(await database.readList('account-a'), isNull);
    expect(scope.current, isNull);
    await expectLater(
      repository.getRoom('room-id'),
      throwsA(isA<RoomApiException>()),
    );
  });
  test('late /me discovery cannot reopen a cleared session', () async {
    final upgradedIdentity = MemoryCacheIdentity();
    final upgraded = RoomCacheSession(database, upgradedIdentity);
    final response = Completer<String>();
    when(() => api.accountId('session-one')).thenAnswer((_) => response.future);
    final repo = CachedRoomRepository(api, upgraded, tokensStore);
    final request = repo.getRoom('room-id');
    await Future<void>.delayed(const Duration(milliseconds: 20));
    await upgraded.clear();
    response.complete('account-a');
    await expectLater(request, throwsA(isA<RoomApiException>()));
    expect(upgraded.current, isNull);
  });
  test(
    'malformed secure owner metadata never produces a cache owner',
    () async {
      FlutterSecureStorage.setMockInitialValues({
        SecureCacheIdentityStore.key: '{bad json',
      });
      expect(
        await SecureCacheIdentityStore(const FlutterSecureStorage()).read(),
        isNull,
      );
    },
  );
  testWidgets(
    'offline detail shows local last-sync time and hides management actions',
    (tester) async {
      await seed();
      when(
        () => api.getRoom('room-id', 'session-one'),
      ).thenThrow(const RoomApiException(code: 'OFFLINE_READ_ONLY'));
      final controller = RoomDetailController(repository, 'room-id');
      await tester.runAsync(controller.load);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            roomDetailControllerProvider(
              'room-id',
            ).overrideWith((_) => controller),
          ],
          child: const MaterialApp(home: RoomDetailScreen(roomId: 'room-id')),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Cached trip'), findsOneWidget);
      expect(find.text('Cached room data · Read only'), findsOneWidget);
      expect(
        find.text(
          'Last synced ${compactDateTime(sync.subtract(const Duration(hours: 1)))}',
        ),
        findsOneWidget,
      );
      expect(find.byTooltip('Edit room'), findsNothing);
      expect(find.byTooltip('Members'), findsNothing);
      expect(find.text('Personal reminders'), findsNothing);
      await tester.scrollUntilVisible(find.text('2 members'), 150);
      final membershipTile = tester.widget<ListTile>(
        find.ancestor(
          of: find.text('2 members'),
          matching: find.byType(ListTile),
        ),
      );
      expect(membershipTile.onTap, isNull);
    },
  );
  testWidgets(
    'offline policy outage still permits cached home without redirect loops',
    (tester) async {
      await seed();
      when(
        () => api.listRooms(limit: 50, sessionId: 'session-one'),
      ).thenThrow(const RoomApiException(code: 'OFFLINE_READ_ONLY'));
      final authRepo = MockCachedAuthRepository();
      when(
        () => authRepo.restoreSession(any()),
      ).thenAnswer((_) async => SessionRestoreResult.authenticated);
      final auth = AuthController(authRepo);
      await auth.initialize();
      final policy = MockCachedPolicy();
      when(() => policy.accepted).thenReturn(false);
      when(() => policy.phase).thenReturn(PolicyPhase.failure);
      final offline = OfflineState()..setOffline(true);
      final controller = RoomListController(repository);
      await tester.runAsync(controller.load);
      final router = createRouter(auth, null, policy, offline);
      addTearDown(router.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            roomListControllerProvider.overrideWith((_) => controller),
            authControllerProvider.overrideWith((_) => auth),
          ],
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await tester.pumpAndSettle();
      expect(router.routeInformationProvider.value.uri.path, '/home');
      expect(find.text('Cached trip'), findsOneWidget);
      expect(find.text('Cached room data · Read only'), findsOneWidget);
    },
  );
  testWidgets(
    'restored cache reaches home immediately while policy status is loading',
    (tester) async {
      await seed();
      when(
        () => api.listRooms(limit: 50, sessionId: 'session-one'),
      ).thenThrow(const RoomApiException(code: 'OFFLINE_READ_ONLY'));
      final controller = RoomListController(repository);
      await tester.runAsync(controller.load);
      final authRepo = MockCachedAuthRepository();
      when(
        () => authRepo.restoreSession(any()),
      ).thenAnswer((_) async => SessionRestoreResult.authenticated);
      final auth = AuthController(authRepo);
      await auth.initialize();
      final policy = MockCachedPolicy();
      when(() => policy.accepted).thenReturn(false);
      when(() => policy.phase).thenReturn(PolicyPhase.loading);
      final router = createRouter(
        auth,
        null,
        policy,
        OfflineState(),
        () => true,
      );
      addTearDown(router.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            roomListControllerProvider.overrideWith((_) => controller),
            authControllerProvider.overrideWith((_) => auth),
          ],
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await tester.pumpAndSettle();
      expect(router.routeInformationProvider.value.uri.path, '/home');
      expect(find.text('Cached trip'), findsOneWidget);
    },
  );
  test(
    'offline guard rejects create/edit/join/member writes before dispatch',
    () async {
      final state = OfflineState()..setOffline(true);
      final dio = Dio()..interceptors.add(OfflineWriteInterceptor(state));
      DioAdapter(dio: dio);
      final roomApi = RoomApi(dio);
      final invites = InvitationApi(Dio(), dio);
      final members = MemberApi(dio);
      await expectLater(
        roomApi.createRoom(fixtures.draft()),
        throwsA(
          isA<RoomApiException>().having(
            (error) => error.code,
            'code',
            'OFFLINE_READ_ONLY',
          ),
        ),
      );
      await expectLater(
        roomApi.updateRoom(
          roomId: 'room-id',
          etag: 'revision',
          draft: fixtures.draft(),
        ),
        throwsA(
          isA<RoomApiException>().having(
            (error) => error.code,
            'code',
            'OFFLINE_READ_ONLY',
          ),
        ),
      );
      await expectLater(
        invites.joinInvitation('preview'),
        throwsA(
          isA<InvitationApiException>().having(
            (error) => error.code,
            'code',
            'OFFLINE_READ_ONLY',
          ),
        ),
      );
      await expectLater(
        members.change('room-id', 'member', MemberAction.remove, 'revision'),
        throwsA(
          isA<RoomApiException>().having(
            (error) => error.code,
            'code',
            'OFFLINE_READ_ONLY',
          ),
        ),
      );
      expect(
        memberErrorMessage(const RoomApiException(code: 'OFFLINE_READ_ONLY')),
        offlineReadOnlyMessage,
      );
    },
  );
  test(
    'successful authenticated read clears offline state and permits a new explicit write',
    () async {
      final state = OfflineState()..setOffline(true);
      final dio = Dio()..interceptors.add(OfflineWriteInterceptor(state));
      final adapter = DioAdapter(dio: dio);
      adapter.onGet('/rooms', (server) => server.reply(200, {'items': []}));
      adapter.onPost('/rooms', (server) => server.reply(201, {}));
      await dio.get<void>('/rooms');
      expect(state.offline, isFalse);
      await dio.post<void>('/rooms');
    },
  );
  test(
    'remote account deletion clears protected cache through authenticated error handling',
    () async {
      await seed();
      final decorated = CacheTokenStore(tokensStore, scope);
      final dio = Dio();
      dio.interceptors.add(
        AuthenticationInterceptor(dio, decorated, FailingRefresher()),
      );
      final adapter = DioAdapter(dio: dio);
      adapter.onGet(
        '/rooms',
        (server) => server.reply(403, {'code': 'ACCOUNT_DELETION_PENDING'}),
      );
      await expectLater(dio.get<void>('/rooms'), throwsA(isA<DioException>()));
      expect(await database.readList('account-a'), isNull);
      expect(tokensStore.value, isNull);
      expect(identities.value, isNull);
    },
  );
  test(
    'cache cleanup failure still clears credentials and disables the old scope',
    () async {
      final broken = MockBrokenCache();
      when(broken.erase).thenThrow(Exception('storage unavailable'));
      final isolated = RoomCacheSession(broken, identities);
      final decorated = CacheTokenStore(tokensStore, isolated);
      await expectLater(decorated.clear(), throwsException);
      expect(tokensStore.value, isNull);
      expect(identities.value, isNull);
      expect(isolated.blocked, isTrue);
    },
  );
  test(
    'credential replacement cannot read the previous account cache',
    () async {
      await seed();
      await tokensStore.write(tokens('two'));
      expect(await repository.cachedList(), isNull);
      expect(await database.readList('account-a'), isNull);
      when(
        () => api.accountId('session-two'),
      ).thenAnswer((_) async => 'account-b');
      when(
        () => api.listRooms(limit: 50, sessionId: 'session-two'),
      ).thenAnswer((_) async => [fixtures.room(title: 'B room')]);
      expect((await repository.listRooms()).single.title, 'B room');
      expect((await repository.cachedList())!.rooms.single.title, 'B room');
      expect(scope.current!.accountId, 'account-b');
    },
  );
  test('nested member access loss invalidates cached room', () async {
    await seed();
    final dio = Dio()..interceptors.add(CacheInvalidationInterceptor(scope));
    final adapter = DioAdapter(dio: dio);
    adapter.onGet('/rooms/room-id/members', (server) => server.reply(404, {}));
    await expectLater(
      dio.get<void>('/rooms/room-id/members'),
      throwsA(isA<DioException>()),
    );
    expect(await repository.cachedDetail('room-id'), isNull);
  });
  test(
    'missing-room mutation response also removes protected cached data',
    () async {
      await seed();
      final dio = Dio()..interceptors.add(CacheInvalidationInterceptor(scope));
      final adapter = DioAdapter(dio: dio);
      adapter.onDelete(
        '/rooms/room-id/permanent',
        (server) => server.reply(404, {}),
      );
      await expectLater(
        dio.delete<void>('/rooms/room-id/permanent'),
        throwsA(isA<DioException>()),
      );
      expect(await repository.cachedDetail('room-id'), isNull);
    },
  );
  test('server failures do not falsely declare device offline', () async {
    final state = OfflineState();
    final dio = Dio()..interceptors.add(OfflineWriteInterceptor(state));
    final adapter = DioAdapter(dio: dio);
    adapter.onGet('/rooms', (server) => server.reply(500, {}));
    await expectLater(dio.get<void>('/rooms'), throwsA(isA<DioException>()));
    expect(state.offline, isFalse);
  });
}
