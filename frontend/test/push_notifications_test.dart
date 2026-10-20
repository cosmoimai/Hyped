import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:hyped/core/api/authentication_interceptor.dart';
import 'package:hyped/core/auth/auth_tokens.dart';
import 'package:hyped/features/authentication/data/auth_api.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/notifications/data/notification_platform.dart';
import 'package:hyped/features/notifications/data/notification_token_store.dart';
import 'package:hyped/features/notifications/data/push_api.dart';
import 'package:hyped/features/notifications/presentation/notification_settings_screen.dart';
import 'package:hyped/features/notifications/presentation/push_controller.dart';
import 'package:hyped/features/notifications/presentation/push_providers.dart';
import 'package:mocktail/mocktail.dart';

import 'helpers/fakes.dart';

const _session = '019b1f20-4152-7ce8-ae9e-b12b87fe8a31';
const _otherSession = '019b1f20-4152-7ce8-ae9e-b12b87fe8a32';
const _room = '019b1f33-e664-7ef4-985e-76b3ac298620';

AuthTokens _tokens([String session = _session]) => AuthTokens(
  accessToken: 'access',
  refreshToken: 'refresh',
  sessionId: session,
  accessTokenExpiresAt: DateTime.utc(2030),
  refreshTokenExpiresAt: DateTime.utc(2031),
);

class _Platform extends NoopNotificationPlatform {
  int currentEpoch = 0;
  bool allowed = true;
  bool enabled = true;
  String value = 'fcm-one';
  String? activated;
  int tokenReads = 0;
  int clears = 0;
  int deletions = 0;
  int permissionRequests = 0;
  Completer<String?>? tokenGate;
  void Function()? changed;
  void Function(Object?)? tapped;
  @override
  int get epoch => currentEpoch;
  @override
  Future<void> startSession() async {
    currentEpoch++;
    allowed = true;
  }

  @override
  Future<void> clearSession() async {
    currentEpoch++;
    allowed = false;
    activated = null;
    clears++;
  }

  @override
  Future<bool> activate(String session, int epoch) async {
    if (!allowed || epoch != currentEpoch) return false;
    activated = session;
    return true;
  }

  @override
  Future<PushStatus> status() async =>
      PushStatus(supported: true, configured: true, enabled: enabled);
  @override
  Future<bool> requestPermission() async {
    permissionRequests++;
    return enabled;
  }

  @override
  Future<String?> token() async {
    tokenReads++;
    return tokenGate == null ? value : tokenGate!.future;
  }

  @override
  Future<void> deleteToken() async {
    deletions++;
  }

  @override
  Future<void> listen(
    void Function() tokenChanged,
    void Function(Object?) tap,
  ) async {
    changed = tokenChanged;
    tapped = tap;
  }
}

class _Api extends PushApi {
  _Api() : super(Dio());
  final registrations = <String>[];
  int removals = 0;
  bool fail = false;
  @override
  Future<void> register(
    String installation,
    String session,
    String token,
    String version,
  ) async {
    if (fail) throw StateError('secret-fcm-token');
    registrations.add('$session:$token');
  }

  @override
  Future<void> remove(String installation, String session) async {
    removals++;
  }
}

class _AuthApi extends Mock implements AuthApi {}

Future<void> _drain(PushController controller) async {
  for (var i = 0; i < 20; i++) {
    await Future<void>.delayed(Duration.zero);
    if (!controller.busy) return;
  }
  fail('Notification synchronization did not finish');
}

void main() {
  late _Platform platform;
  late _Api api;
  late MemoryTokenStore store;
  late PushController controller;
  bool providerOwnsController = false;
  setUp(() async {
    providerOwnsController = false;
    platform = _Platform();
    api = _Api();
    store = MemoryTokenStore(_tokens());
    controller = PushController(platform, store, api, () async => '1.0');
    await controller.initialize();
  });
  tearDown(() {
    if (!providerOwnsController) controller.dispose();
  });

  test(
    'registers current session and rotates token while suppressing redundant sync',
    () async {
      controller.setAuthenticated(true);
      await _drain(controller);
      expect(api.registrations, ['$_session:fcm-one']);
      expect(platform.activated, _session);
      await controller.sync();
      expect(api.registrations, hasLength(1));
      platform.value = 'fcm-two';
      platform.changed!();
      await _drain(controller);
      expect(api.registrations, ['$_session:fcm-one', '$_session:fcm-two']);
    },
  );

  test(
    'denied permission removes registration and never obtains FCM token',
    () async {
      platform.enabled = false;
      controller.setAuthenticated(true);
      await _drain(controller);
      await controller.requestPermission();
      expect(platform.permissionRequests, 1);
      expect(platform.tokenReads, 0);
      expect(api.registrations, isEmpty);
      expect(api.removals, greaterThan(0));
      expect(controller.message, contains('You can still use all rooms'));
    },
  );

  test('tap routes only the current session and is consumed once', () async {
    controller.setAuthenticated(true);
    await _drain(controller);
    platform.tapped!({'sessionId': _session, 'roomId': _room});
    expect(await controller.takePendingLocation(), '/rooms/$_room');
    expect(await controller.takePendingLocation(), isNull);
    platform.tapped!({'sessionId': _otherSession, 'roomId': _room});
    expect(await controller.takePendingLocation(), isNull);
  });

  test('cold tap can wait for authentication initialization', () async {
    platform.tapped!({'sessionId': _session, 'roomId': _room});
    expect(await controller.takePendingLocation(), isNull);
    controller.setAuthenticated(true);
    await _drain(controller);
    expect(await controller.takePendingLocation(), '/rooms/$_room');
  });

  test('malformed notification locations cannot navigate', () async {
    controller.setAuthenticated(true);
    await _drain(controller);
    platform.tapped!({
      'sessionId': _session,
      'roomId': '../../sign-in?token=secret',
    });
    expect(await controller.takePendingLocation(), isNull);
  });

  test(
    'logout clears pending navigation, local notifications and SDK token',
    () async {
      controller.setAuthenticated(true);
      await _drain(controller);
      platform.tapped!({'sessionId': _session, 'roomId': _room});
      controller.setAuthenticated(false);
      await NotificationTokenStore(store, platform).clear();
      expect(store.value, isNull);
      expect(platform.activated, isNull);
      expect(platform.deletions, 1);
      expect(await controller.takePendingLocation(), isNull);
    },
  );

  test(
    'late token read after account change cannot register old account',
    () async {
      platform.tokenGate = Completer<String?>();
      controller.setAuthenticated(true);
      await Future<void>.delayed(Duration.zero);
      controller.setAuthenticated(false);
      await platform.clearSession();
      store.value = _tokens(_otherSession);
      await platform.startSession();
      platform.tokenGate!.complete('old-token');
      await _drain(controller);
      expect(api.registrations, isEmpty);
      platform.tokenGate = null;
      platform.value = 'new-account-token';
      controller.setAuthenticated(true);
      await _drain(controller);
      expect(api.registrations, ['$_otherSession:new-account-token']);
    },
  );

  test('Firebase/API failures expose safe recoverable messages', () async {
    api.fail = true;
    controller.setAuthenticated(true);
    await _drain(controller);
    expect(controller.message, contains('retry'));
    expect(controller.message, isNot(contains('secret-fcm-token')));
    api.fail = false;
    await controller.sync(force: true);
    expect(api.registrations, hasLength(1));
  });

  test(
    'real logout clears native state even when backend revocation fails',
    () async {
      final authApi = _AuthApi();
      when(authApi.logout).thenThrow(const AuthenticationApiException());
      final auth = AuthRepository(
        authApi,
        NotificationTokenStore(store, platform),
        FakeDeviceMetadataProvider(),
        null,
        null,
        platform,
      );
      await expectLater(
        auth.logout(),
        throwsA(isA<AuthenticationApiException>()),
      );
      expect(platform.clears, greaterThan(0));
      expect(platform.deletions, 1);
      expect(store.value, isNull);
    },
  );

  test(
    'push API follows device contract and sanitizes backend failures',
    () async {
      final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
      final adapter = DioAdapter(dio: dio);
      dio.httpClientAdapter = adapter;
      adapter.onPut(
        '/devices/installation',
        (server) => server.reply(204, null),
        data: {
          'platform': 'ANDROID',
          'fcmToken': 'secret',
          'notificationsEnabled': true,
          'appVersion': '1.0',
        },
      );
      adapter.onDelete(
        '/devices/installation',
        (server) => server.reply(204, null),
      );
      final actual = PushApi(dio);
      await actual.register('installation', _session, 'secret', '1.0');
      await actual.remove('installation', _session);
      adapter.onPut(
        '/devices/installation',
        (server) => server.reply(500, {'message': 'secret'}),
        data: {
          'platform': 'ANDROID',
          'fcmToken': 'secret',
          'notificationsEnabled': true,
          'appVersion': '1.0',
        },
      );
      await expectLater(
        actual.register('installation', _session, 'secret', '1.0'),
        throwsA(
          isA<PushApiException>().having(
            (error) => error.toString(),
            'safe message',
            isNot(contains('secret')),
          ),
        ),
      );
    },
  );

  test(
    'authentication interceptor rejects registration after session changes',
    () async {
      final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
      dio.interceptors.add(
        AuthenticationInterceptor(dio, store, FakeRefresher(_tokens())),
      );
      await expectLater(
        PushApi(dio).register('installation', _otherSession, 'secret', '1.0'),
        throwsA(isA<PushApiException>()),
      );
    },
  );

  test('a delayed refresh cannot overwrite the next account session', () async {
    final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
    final gate = Completer<void>();
    final refresher = FakeRefresher(_tokens(), gate: gate);
    dio.interceptors.add(AuthenticationInterceptor(dio, store, refresher));
    final adapter = DioAdapter(dio: dio);
    dio.httpClientAdapter = adapter;
    adapter.onPut(
      '/devices/installation',
      (server) => server.reply(401, {}),
      data: {
        'platform': 'ANDROID',
        'fcmToken': 'secret',
        'notificationsEnabled': true,
        'appVersion': '1.0',
      },
    );
    final request = expectLater(
      PushApi(dio).register('installation', _session, 'secret', '1.0'),
      throwsA(isA<PushApiException>()),
    );
    for (var i = 0; i < 100 && refresher.calls == 0; i++) {
      await Future<void>.delayed(const Duration(milliseconds: 1));
    }
    expect(refresher.calls, 1);
    store.value = _tokens(_otherSession);
    gate.complete();
    await request;
    expect(store.value?.sessionId, _otherSession);
    expect(store.clearCount, 0);
  });

  testWidgets(
    'permission onboarding explains fixed reminders and requests only after opt-in',
    (tester) async {
      providerOwnsController = true;
      platform.enabled = false;
      controller.setAuthenticated(true);
      await tester.pump();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [pushControllerProvider.overrideWith((ref) => controller)],
          child: const MaterialApp(home: NotificationSettingsScreen()),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.textContaining('24 hours before, 1 hour before'),
        findsOneWidget,
      );
      expect(platform.permissionRequests, 0);
      await tester.tap(find.text('Enable notifications'));
      await tester.pumpAndSettle();
      expect(platform.permissionRequests, 1);
      expect(
        find.textContaining('You can still use all rooms'),
        findsOneWidget,
      );
      // ProviderScope owns the overridden controller for this test.
    },
  );
}
