import 'package:hyped/core/auth/auth_tokens.dart';
import 'dart:async';
import 'package:hyped/core/api/authentication_interceptor.dart';
import 'helpers/fakes.dart';
import 'dart:convert';
import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/core/api/api_environment.dart';
import 'package:hyped/core/api/integrity_interceptor.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'release rejects HTTP, placeholders, credentials, IPs and malformed API URLs',
    () {
      for (final url in [
        'http://api.hyped.app/api/v1',
        'https://localhost/api/v1',
        'https://api.example.com/api/v1',
        'https://api.test/api/v1',
        'https://10.0.2.2/api/v1',
        'https://api.invalid/api/v1',
        'https://user:password@api.hyped.app/api/v1',
        'https://api.hyped.app:443/api/v1',
        'https://api.hyped.app/api/v1?secret=x',
        'https://api.hyped.app/api/v1#fragment',
        'https://api.hyped.app/other',
        'not a URL',
      ]) {
        expect(
          () => ApiEnvironment.validate(url, release: true),
          throwsStateError,
        );
      }
      expect(
        () => ApiEnvironment.validate(
          'https://api.hyped.app/api/v1',
          release: true,
        ),
        returnsNormally,
      );
      expect(
        () => ApiEnvironment.validate(
          'http://10.0.2.2:8080/api/v1',
          release: false,
        ),
        returnsNormally,
      );
    },
  );
  test(
    'binding includes exact body, account credential, unique ID and revision',
    () {
      String binding({
        String token = 'a',
        String body = '{}',
        String id = 'id',
        String revision = 'r',
      }) => integrityBinding(
        method: 'POST',
        uri: Uri.parse('https://api.hyped.app/api/v1/rooms'),
        body: utf8.encode(body),
        authorization: token,
        id: id,
        time: 'time',
        revision: revision,
      );
      expect(
        integrityBinding(
          method: 'PATCH',
          uri: Uri.parse('https://api.hyped.app/api/v1/rooms/123?page=1'),
          body: utf8.encode('{"title":"世界"}'),
          authorization: 'Bearer fixture-access-token',
          id: '00000000-0000-4000-8000-000000000001',
          time: '1792843200000',
          idempotency: 'fixture-key',
          revision: '"room-2"',
        ),
        '2hxkhzjVPhT4LfhHr1pRAkhgtU050CjCQ7IZwg2_VOg',
      );
      final base = binding();
      expect(base, hasLength(43));
      expect(binding(token: 'b'), isNot(base));
      expect(binding(body: '{"title":"changed"}'), isNot(base));
      expect(binding(id: 'new'), isNot(base));
      expect(binding(revision: 'new'), isNot(base));
    },
  );
  test(
    'authenticated write sends fresh evidence and freezes exact UTF8 JSON',
    () async {
      final dio = Dio(
        BaseOptions(
          baseUrl: 'https://api.hyped.app/api/v1',
          headers: {'Authorization': 'Bearer private'},
        ),
      );
      final adapter = DioAdapter(dio: dio);
      final store = MemoryTokenStore(tokens());
      dio.interceptors.add(
        AuthenticationInterceptor(dio, store, FakeRefresher(tokens())),
      );
      var calls = 0;
      final hashes = <String>[];
      dio.interceptors.add(
        IntegrityInterceptor(
          (hash) async {
            hashes.add(hash);
            return 'opaque-evidence';
          },
          id: () => 'id-${++calls}',
          currentSession: () async => store.value?.sessionId,
        ),
      );
      final requests = <RequestOptions>[];
      dio.interceptors.add(
        InterceptorsWrapper(
          onRequest: (o, h) {
            requests.add(o);
            h.next(o);
          },
        ),
      );
      adapter.onPost('/rooms', (s) => s.reply(200, {}), data: '{"title":"世界"}');
      await dio.post<dynamic>('/rooms', data: {'title': '世界'});
      await dio.post<dynamic>('/rooms', data: {'title': '世界'});
      expect(requests.first.data, '{"title":"世界"}');
      expect(requests.first.headers['X-Play-Integrity'], 'opaque-evidence');
      expect(hashes.first, isNot(hashes.last));
    },
  );
  test(
    'missing evidence leaves server observe/enforce decision authoritative',
    () async {
      final dio = Dio(
        BaseOptions(
          baseUrl: 'https://api.hyped.app/api/v1',
          headers: {
            'Authorization': 'Bearer private',
            'X-Play-Integrity': 'old',
          },
        ),
      );
      final adapter = DioAdapter(dio: dio);
      dio.interceptors.add(IntegrityInterceptor((_) async => null));
      RequestOptions? seen;
      dio.interceptors.add(
        InterceptorsWrapper(
          onRequest: (o, h) {
            seen = o;
            h.next(o);
          },
        ),
      );
      adapter.onPost(
        '/rooms',
        (s) => s.reply(403, {'code': 'INTEGRITY_REQUIRED'}),
      );
      await expectLater(
        dio.post<dynamic>('/rooms'),
        throwsA(isA<DioException>()),
      );
      expect(seen!.headers.containsKey('X-Play-Integrity'), isFalse);
      expect(seen!.headers.containsKey('X-Integrity-Id'), isFalse);
    },
  );
  test('public reads auth and cleanup never acquire attestation', () {
    for (final entry in [
      ('GET', '/api/v1/rooms'),
      ('POST', '/api/v1/public/invitations/preview'),
      ('POST', '/api/v1/auth/logout'),
      ('POST', '/api/v1/auth/refresh'),
      ('POST', '/api/v1/me/deletion-requests'),
    ]) {
      expect(integrityProtected(entry.$1, entry.$2), isFalse);
    }
    expect(integrityProtected('PATCH', '/api/v1/rooms/id'), isTrue);
  });
  test(
    'native platform failures return no evidence without raw diagnostics',
    () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      const channel = MethodChannel('hyped/play_integrity');
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(
        channel,
        (_) async =>
            throw PlatformException(code: 'UNAVAILABLE', message: 'PRIVATE'),
      );
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
      expect(await AndroidIntegrityPlatform().token('A' * 43), isNull);
    },
  );

  test(
    'authenticated refresh obtains new evidence without changing business idempotency',
    () async {
      final store = MemoryTokenStore(tokens());
      final old = tokens();
      final refresher = FakeRefresher(
        AuthTokens(
          accessToken: 'access-two',
          refreshToken: 'refresh-two',
          accessTokenExpiresAt: old.accessTokenExpiresAt,
          refreshTokenExpiresAt: old.refreshTokenExpiresAt,
          sessionId: old.sessionId,
        ),
      );
      final dio = Dio(BaseOptions(baseUrl: 'https://api.hyped.app/api/v1'));
      final adapter = DioAdapter(dio: dio);
      dio.interceptors.add(AuthenticationInterceptor(dio, store, refresher));
      final hashes = <String>[];
      dio.interceptors.add(
        IntegrityInterceptor((hash) async {
          hashes.add(hash);
          return 'evidence';
        }, currentSession: () async => store.value?.sessionId),
      );
      final keys = <Object?>[];
      dio.interceptors.add(
        InterceptorsWrapper(
          onRequest: (o, h) {
            keys.add(o.headers['Idempotency-Key']);
            h.next(o);
          },
        ),
      );
      adapter.onPost(
        '/rooms',
        (server) => server.reply(401, {'code': 'ACCESS_TOKEN_INVALID'}),
        data: '{}',
        headers: {'Authorization': 'Bearer access-one'},
      );
      adapter.onPost(
        '/rooms',
        (server) => server.reply(200, {}),
        data: '{}',
        headers: {'Authorization': 'Bearer access-two'},
      );
      expect(
        (await dio.post<dynamic>(
          '/rooms',
          data: <String, dynamic>{},
          options: Options(headers: {'Idempotency-Key': 'fixture-key'}),
        )).statusCode,
        200,
      );
      expect(refresher.calls, 1);
      expect(hashes, hasLength(2));
      expect(hashes.first, isNot(hashes.last));
      expect(keys, ['fixture-key', 'fixture-key']);
      expect(store.value?.toString(), isNot(contains(old.sessionId)));
    },
  );

  for (final replacement in [null, tokens('another-account')]) {
    test(
      'cancel delayed evidence after ${replacement == null ? 'logout' : 'account change'}',
      () async {
        final store = MemoryTokenStore(tokens());
        final dio = Dio(BaseOptions(baseUrl: 'https://api.hyped.app/api/v1'));
        final adapter = DioAdapter(dio: dio);
        var sent = 0;
        adapter.onPost('/rooms', (server) => server.reply(200, {}), data: '{}');
        dio.interceptors.add(
          AuthenticationInterceptor(dio, store, FakeRefresher(tokens())),
        );
        final started = Completer<void>();
        final gate = Completer<String?>();
        dio.interceptors.add(
          IntegrityInterceptor((_) {
            started.complete();
            return gate.future;
          }, currentSession: () async => store.value?.sessionId),
        );
        dio.interceptors.add(
          InterceptorsWrapper(
            onRequest: (o, h) {
              sent++;
              h.next(o);
            },
          ),
        );
        final request = dio.post<dynamic>('/rooms', data: <String, dynamic>{});
        final rejected = expectLater(
          request,
          throwsA(
            isA<DioException>().having(
              (e) => e.type,
              'cancelled',
              DioExceptionType.cancel,
            ),
          ),
        );
        await started.future;
        store.value = replacement;
        gate.complete('evidence');
        await rejected;
        expect(sent, 0);
      },
    );
  }
  test(
    'a failed protected write cannot retry as a different signed-in account',
    () async {
      final store = MemoryTokenStore(tokens());
      final refresher = FakeRefresher(tokens('refresh'));
      final dio = Dio(BaseOptions(baseUrl: 'https://api.hyped.app/api/v1'));
      final adapter = DioAdapter(dio: dio);
      dio.interceptors.add(AuthenticationInterceptor(dio, store, refresher));
      dio.interceptors.add(
        IntegrityInterceptor(
          (_) async => 'evidence',
          currentSession: () async => store.value?.sessionId,
        ),
      );
      var sent = 0;
      adapter.onPost('/rooms', (server) => server.reply(200, {}), data: '{}');
      dio.interceptors.add(
        InterceptorsWrapper(
          onRequest: (o, h) {
            sent++;
            store.value = tokens('another-account');
            h.reject(
              DioException(
                requestOptions: o,
                response: Response(
                  requestOptions: o,
                  statusCode: 401,
                  data: {'code': 'ACCESS_TOKEN_INVALID'},
                ),
              ),
            );
          },
        ),
      );
      await expectLater(
        dio.post<dynamic>('/rooms', data: <String, dynamic>{}),
        throwsA(isA<DioException>()),
      );
      expect(sent, 1);
      expect(refresher.calls, 0);
      expect(store.value?.sessionId, 'session-another-account');
      expect(store.clearCount, 0);
    },
  );
  test(
    'source manifest keeps protected components private and excludes advertising permissions',
    () {
      final manifest = File(
        'android/app/src/main/AndroidManifest.xml',
      ).readAsStringSync();
      expect(manifest, contains('android:usesCleartextTraffic="false"'));
      expect(manifest, contains('android:allowBackup="false"'));
      expect(
        manifest,
        contains(
          '<service android:name=".HypedMessagingService" android:exported="false"',
        ),
      );
      expect(manifest, contains('android:autoVerify="true"'));
      expect(
        manifest,
        contains(
          'android:name="com.google.android.gms.permission.AD_ID" tools:node="remove"',
        ),
      );
      expect(
        File(
          'android/app/src/main/res/xml/network_security_config.xml',
        ).readAsStringSync(),
        contains('cleartextTrafficPermitted="false"'),
      );
      expect(
        File(
          'android/app/src/main/res/xml/widget_data_extraction_rules.xml',
        ).readAsStringSync(),
        contains('<exclude domain="sharedpref" path="." />'),
      );
    },
  );
}
