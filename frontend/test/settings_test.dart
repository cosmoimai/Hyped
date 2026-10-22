import 'dart:async';
import 'package:dio/dio.dart';
import 'package:hyped/core/api/authentication_interceptor.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:hyped/features/authentication/data/auth_api.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/settings/data/account_api.dart';
import 'package:hyped/features/settings/data/recent_authentication.dart';
import 'package:hyped/features/settings/data/theme_preference.dart';
import 'package:hyped/features/settings/presentation/settings_controller.dart';
import 'package:hyped/features/settings/presentation/settings_screen.dart';
import 'package:mocktail/mocktail.dart';
import 'helpers/fakes.dart';

class MockAccountApi extends Mock implements AccountApi {}

class MockAuthRepository extends Mock implements AuthRepository {}

class MockRecent extends Mock implements RecentAuthentication {}

class MockLogoutApi extends Mock implements AuthApi {}

class MockIdentity extends Mock implements IdentityProvider {}

class MemoryTheme implements ThemePreferenceStore {
  String? value;
  bool fail = false;
  @override
  Future<String?> read() async => value;
  @override
  Future<void> write(String value) async {
    if (fail) throw StateError('storage failed');
    this.value = value;
  }
}

class RecordingWidgets extends NoopRoomWidgetRepository {
  int cleared = 0;
  bool fail = false;
  @override
  Future<void> clearProtectedContent() async {
    cleared++;
    if (fail) throw StateError('bridge failed');
  }
}

const profile = AccountProfile('Mira', 2, 1, 3);
final current = ActiveDevice.fromJson({
  'sessionId': 'internal-current-session',
  'deviceName': 'Galaxy',
  'platform': 'ANDROID',
  'lastUsedAt': '2030-01-02T00:00:00Z',
  'isCurrent': true,
});
final other = ActiveDevice.fromJson({
  'sessionId': 'internal-other-session',
  'deviceName': 'Pixel',
  'platform': 'ANDROID',
  'lastUsedAt': '2030-01-01T00:00:00Z',
  'isCurrent': false,
});
void main() {
  late MockAccountApi api;
  late MockRecent recent;
  late MockAuthRepository repository;
  late AuthController auth;
  late MemoryTokenStore tokensStore;
  late SettingsController controller;
  setUp(() async {
    api = MockAccountApi();
    recent = MockRecent();
    repository = MockAuthRepository();
    tokensStore = MemoryTokenStore(tokens());
    when(
      () => repository.restoreSession(any()),
    ).thenAnswer((_) async => SessionRestoreResult.authenticated);
    when(
      () => repository.clearLocalSession(
        expectedSessionId: any(named: 'expectedSessionId'),
      ),
    ).thenAnswer((_) async {
      await tokensStore.clear();
      return true;
    });
    when(() => repository.logout()).thenAnswer((_) async {
      await tokensStore.clear();
    });
    auth = AuthController(repository);
    await auth.initialize();
    controller = SettingsController(api, auth, recent, tokensStore);
    when(api.profile).thenAnswer((_) async => profile);
    when(api.devices).thenAnswer((_) async => [current, other]);
    when(api.readiness).thenAnswer((_) async => const DeletionReadiness([], 0));
    when(recent.token).thenAnswer((_) async => 'recent-proof');
  });
  tearDown(() {
    auth.dispose();
  });

  test('safe authenticated profile and verified current device load', () async {
    await controller.load();
    expect(controller.profile!.displayName, 'Mira');
    expect(controller.devices.first.isCurrent, isTrue);
    expect(controller.error, isNull);
    controller.dispose();
  });
  test('profile error leaves clean retry state', () async {
    when(
      api.profile,
    ).thenThrow(const AccountApiException('DEPENDENCY_UNAVAILABLE', 503));
    await controller.load();
    expect(controller.profile, isNull);
    expect(controller.loading, isFalse);
    expect(controller.error, contains('retry'));
    controller.dispose();
  });
  test(
    'missing current marker treats the verified session as expired and cleans up',
    () async {
      when(api.devices).thenAnswer((_) async => [other]);
      await controller.load();
      expect(auth.state.status, AuthStatus.signedOut);
      expect(tokensStore.value, isNull);
      controller.dispose();
    },
  );
  test(
    'revoking another device refreshes list without signing out current user',
    () async {
      when(() => api.revoke(other, 'session-one')).thenAnswer((_) async {});
      when(api.devices).thenAnswer((_) async => [current]);
      expect(await controller.revoke(other), isTrue);
      expect(controller.devices, [current]);
      expect(auth.state.isAuthenticated, isTrue);
      controller.dispose();
    },
  );
  test('current-device revocation clears credentials and signs out', () async {
    when(() => api.revoke(current, 'session-one')).thenAnswer((_) async {});
    expect(await controller.revoke(current), isTrue);
    expect(auth.state.status, AuthStatus.signedOut);
    expect(tokensStore.value, isNull);
    controller.dispose();
  });
  test('failed revocation preserves current account', () async {
    when(
      () => api.revoke(other, 'session-one'),
    ).thenThrow(const AccountApiException(null));
    expect(await controller.revoke(other), isFalse);
    expect(tokensStore.value, isNotNull);
    controller.dispose();
  });
  test(
    'deletion requires readiness and exact deliberate confirmation',
    () async {
      expect(await controller.delete('DELETE'), isFalse);
      await controller.checkReadiness();
      expect(await controller.delete('delete'), isFalse);
      verifyNever(recent.token);
      controller.dispose();
    },
  );
  test(
    'account deletion success clears local session and safe cached account state',
    () async {
      when(
        () => api.delete('recent-proof', 'session-one'),
      ).thenAnswer((_) async {});
      await controller.load();
      await controller.checkReadiness();
      expect(await controller.delete('DELETE'), isTrue);
      expect(tokensStore.value, isNull);
      expect(auth.state.status, AuthStatus.signedOut);
      expect(controller.profile, isNull);
      expect(controller.devices, isEmpty);
      expect(controller.readiness, isNull);
      controller.dispose();
    },
  );
  test(
    'deletion failure before acceptance preserves credentials and offers fresh proof retry',
    () async {
      when(() => api.delete('recent-proof', 'session-one')).thenThrow(
        const AccountApiException('RECENT_AUTHENTICATION_REQUIRED', 401),
      );
      await controller.checkReadiness();
      expect(await controller.delete('DELETE'), isFalse);
      expect(controller.error, contains('Google again'));
      expect(auth.state.isAuthenticated, isTrue);
      expect(tokensStore.value, isNotNull);
      controller.dispose();
    },
  );
  test('reauthentication cancellation never calls backend deletion', () async {
    when(recent.token).thenThrow(const IdentitySelectionCancelledException());
    await controller.checkReadiness();
    expect(await controller.delete('DELETE'), isFalse);
    verifyNever(() => api.delete('recent-proof', 'session-one'));
    expect(tokensStore.value, isNotNull);
    controller.dispose();
  });
  test(
    'already accepted deletion response still clears local credentials',
    () async {
      when(
        () => api.delete('recent-proof', 'session-one'),
      ).thenThrow(const AccountApiException('ACCOUNT_DELETION_PENDING', 403));
      await controller.checkReadiness();
      expect(await controller.delete('DELETE'), isTrue);
      expect(tokensStore.value, isNull);
      controller.dispose();
    },
  );
  test(
    'account change during reauthentication cannot delete or clear the new session',
    () async {
      final gate = Completer<String>();
      when(recent.token).thenAnswer((_) => gate.future);
      await controller.checkReadiness();
      final operation = controller.delete('DELETE');
      await Future<void>.delayed(Duration.zero);
      await tokensStore.write(tokens('new-account'));
      gate.complete('recent-proof');
      expect(await operation, isFalse);
      verifyNever(() => api.delete('recent-proof', 'session-one'));
      expect(tokensStore.value!.sessionId, 'session-new-account');
      controller.dispose();
    },
  );
  test(
    'late profile response cannot show another account stale data',
    () async {
      final gate = Completer<AccountProfile>();
      when(api.profile).thenAnswer((_) => gate.future);
      final load = controller.load();
      await tokensStore.write(tokens('other'));
      gate.complete(profile);
      await load;
      expect(controller.profile, isNull);
      controller.dispose();
    },
  );
  test(
    'theme choice persists across controller recreation and supports every mode',
    () async {
      final store = MemoryTheme();
      final theme = ThemeController(store);
      for (final mode in ThemeMode.values) {
        await theme.set(mode);
        final restarted = ThemeController(store);
        await restarted.load();
        expect(restarted.mode, mode);
        restarted.dispose();
      }
      theme.dispose();
    },
  );
  test(
    'unknown theme defaults to system and failed save preserves previous choice',
    () async {
      final store = MemoryTheme()..value = 'unknown';
      final theme = ThemeController(store);
      await theme.load();
      expect(theme.mode, ThemeMode.system);
      store.fail = true;
      await theme.set(ThemeMode.dark);
      expect(theme.mode, ThemeMode.system);
      expect(theme.error, isNotNull);
      theme.dispose();
    },
  );
  test(
    'settings API orders devices and never derives current marker from local IDs',
    () async {
      final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
      final adapter = DioAdapter(dio: dio);
      dio.httpClientAdapter = adapter;
      adapter.onGet(
        '/auth/sessions',
        (server) => server.reply(200, {
          'items': [
            {
              'sessionId': 'opaque-old',
              'deviceName': 'Old',
              'platform': 'ANDROID',
              'lastUsedAt': '2030-01-01T00:00:00Z',
              'isCurrent': true,
            },
            {
              'sessionId': 'opaque-new',
              'deviceName': 'New',
              'platform': 'ANDROID',
              'lastUsedAt': '2030-01-02T00:00:00Z',
              'isCurrent': false,
            },
          ],
        }),
      );
      final account = AccountApi(dio);
      final devices = await account.devices();
      expect(devices.map((value) => value.name), ['New', 'Old']);
      expect(devices.last.isCurrent, isTrue);
      adapter.onDelete(
        '/auth/sessions/opaque-new',
        (server) => server.reply(204, null),
      );
      await account.revoke(devices.first, 'session-one');
    },
  );
  for (final fail in [false, true]) {
    test(
      'logout cleans widgets credentials SDK and pending invites when backend failure is $fail',
      () async {
        final remote = MockLogoutApi();
        final widgets = RecordingWidgets()..fail = fail;
        final identity = MockIdentity();
        var clearedPending = false;
        when(identity.signOut).thenAnswer((_) async {});
        if (fail) {
          when(remote.logout).thenThrow(const AuthenticationApiException());
        } else {
          when(remote.logout).thenAnswer((_) async {});
        }
        final repo = AuthRepository(
          remote,
          tokensStore,
          FakeDeviceMetadataProvider(),
          identity,
          widgets,
          null,
          () {
            clearedPending = true;
          },
        );
        try {
          await repo.logout();
        } catch (_) {}
        expect(tokensStore.value, isNull);
        expect(clearedPending, isTrue);
        expect(widgets.cleared, greaterThan(0));
        verify(remote.logout).called(1);
        verify(identity.signOut).called(1);
      },
    );
  }
  test(
    'local cleanup after accepted deletion removes widget and pending-invite state',
    () async {
      final widgets = RecordingWidgets();
      var pending = true;
      final repo = AuthRepository(
        MockLogoutApi(),
        tokensStore,
        FakeDeviceMetadataProvider(),
        null,
        widgets,
        null,
        () {
          pending = false;
        },
      );
      expect(
        await repo.clearLocalSession(expectedSessionId: 'session-one'),
        isTrue,
      );
      expect(tokensStore.value, isNull);
      expect(widgets.cleared, 1);
      expect(pending, isFalse);
    },
  );
  test(
    'recent-authentication failure does not rotate or delete the app session',
    () async {
      final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
      final adapter = DioAdapter(dio: dio);
      dio.httpClientAdapter = adapter;
      final refresher = FakeRefresher(tokens('rotated'));
      dio.interceptors.add(
        AuthenticationInterceptor(dio, tokensStore, refresher),
      );
      adapter.onPost(
        '/me/deletion-requests',
        (server) =>
            server.reply(401, {'code': 'RECENT_AUTHENTICATION_REQUIRED'}),
        data: {'recentFirebaseIdToken': 'proof'},
      );
      await expectLater(
        AccountApi(dio).delete('proof', 'session-one'),
        throwsA(isA<AccountApiException>()),
      );
      expect(refresher.calls, 0);
      expect(tokensStore.value!.sessionId, 'session-one');
    },
  );
  test(
    'remote deletion response clears pending state alongside credentials',
    () async {
      final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
      final adapter = DioAdapter(dio: dio);
      dio.httpClientAdapter = adapter;
      var pending = true;
      dio.interceptors.add(
        AuthenticationInterceptor(
          dio,
          tokensStore,
          FakeRefresher(tokens()),
          onAccountDeleted: () {
            pending = false;
          },
        ),
      );
      adapter.onGet(
        '/me',
        (server) => server.reply(403, {'code': 'ACCOUNT_DELETION_PENDING'}),
      );
      await expectLater(
        AccountApi(dio).profile(),
        throwsA(isA<AccountApiException>()),
      );
      expect(tokensStore.value, isNull);
      expect(pending, isFalse);
    },
  );
  test(
    'deletion API generates a fresh UUID key and only sends recent proof',
    () async {
      final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
      final adapter = DioAdapter(dio: dio);
      dio.httpClientAdapter = adapter;
      final keys = <Object?>[];
      dio.interceptors.add(
        InterceptorsWrapper(
          onRequest: (request, handler) {
            keys.add(request.headers['Idempotency-Key']);
            expect(request.extra['expectedSessionId'], 'session-one');
            expect(request.data, {'recentFirebaseIdToken': 'proof'});
            handler.next(request);
          },
        ),
      );
      adapter.onPost(
        '/me/deletion-requests',
        (server) => server.reply(202, null),
        data: {'recentFirebaseIdToken': 'proof'},
      );
      final account = AccountApi(dio);
      await account.delete('proof', 'session-one');
      await account.delete('proof', 'session-one');
      expect(keys[0], isNot(keys[1]));
      expect(
        keys.every(
          (key) => RegExp(
            r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
          ).hasMatch(key as String),
        ),
        isTrue,
      );
    },
  );

  test('old cleanup cannot clear a replacement account', () async {
    await tokensStore.write(tokens('new'));
    final repo = AuthRepository(
      MockLogoutApi(),
      tokensStore,
      FakeDeviceMetadataProvider(),
    );
    expect(
      await repo.clearLocalSession(expectedSessionId: 'session-one'),
      isFalse,
    );
    expect(tokensStore.value, isNotNull);
  });
  testWidgets(
    'profile screen shows safe fields settings and current device without IDs',
    (tester) async {
      await controller.load();
      final theme = ThemeController(MemoryTheme());
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            settingsControllerProvider.overrideWith((ref) => controller),
            themeControllerProvider.overrideWith((ref) => theme),
          ],
          child: const MaterialApp(home: SettingsScreen()),
        ),
      );
      expect(find.text('Mira'), findsOneWidget);
      expect(find.textContaining('This device'), findsOneWidget);
      expect(find.textContaining('internal-current-session'), findsNothing);
      expect(find.text('Notifications'), findsOneWidget);
      expect(find.text('Per-room reminders'), findsOneWidget);
    },
  );
  testWidgets('revocation confirmation cancellation sends no request', (
    tester,
  ) async {
    await controller.load();
    final theme = ThemeController(MemoryTheme());
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsControllerProvider.overrideWith((ref) => controller),
          themeControllerProvider.overrideWith((ref) => theme),
        ],
        child: const MaterialApp(home: SettingsScreen()),
      ),
    );
    await tester.scrollUntilVisible(find.byTooltip('Revoke device'), 250);
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Revoke device'));
    await tester.pumpAndSettle();
    expect(find.text('Revoke device?'), findsOneWidget);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    verifyNever(() => api.revoke(other, 'session-one'));
  });
  testWidgets(
    'permanent deletion is gated by typed DELETE and an explicit warning',
    (tester) async {
      await controller.load();
      final theme = ThemeController(MemoryTheme());
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            settingsControllerProvider.overrideWith((ref) => controller),
            themeControllerProvider.overrideWith((ref) => theme),
          ],
          child: const MaterialApp(home: SettingsScreen()),
        ),
      );
      await tester.scrollUntilVisible(
        find.text('Permanently delete account'),
        250,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Permanently delete account'));
      await tester.pumpAndSettle();
      expect(find.textContaining('no recovery period'), findsOneWidget);
      final confirm = find.widgetWithText(
        FilledButton,
        'Confirm with Google and delete',
      );
      expect(tester.widget<FilledButton>(confirm).onPressed, isNull);
      await tester.enterText(find.byType(TextField), 'DELETE');
      await tester.pump();
      expect(tester.widget<FilledButton>(confirm).onPressed, isNotNull);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      verifyNever(recent.token);
    },
  );
}
