import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:hyped/core/api/authentication_interceptor.dart';
import 'helpers/fakes.dart';

void main() {
  test('attaches access token to authenticated requests', () async {
    final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
    final store = MemoryTokenStore(tokens());
    final adapter = DioAdapter(dio: dio);
    dio.httpClientAdapter = adapter;
    dio.interceptors.add(
      AuthenticationInterceptor(dio, store, FakeRefresher(tokens('two'))),
    );
    adapter.onGet(
      '/private',
      (server) => server.reply(200, {'ok': true}),
      headers: {'Authorization': 'Bearer access-one'},
    );
    expect((await dio.get<dynamic>('/private')).statusCode, 200);
  });

  test('concurrent 401 responses share one refresh and retry once', () async {
    final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
    final store = MemoryTokenStore(tokens());
    final gate = Completer<void>();
    final refresher = FakeRefresher(tokens('two'), gate: gate);
    final adapter = DioAdapter(dio: dio);
    dio.httpClientAdapter = adapter;
    dio.interceptors.add(AuthenticationInterceptor(dio, store, refresher));
    adapter.onGet(
      '/private',
      (server) => server.reply(200, {'ok': true}),
      headers: {'Authorization': 'Bearer access-two'},
    );
    adapter.onGet(
      '/private',
      (server) => server.reply(401, {'code': 'expired'}),
      headers: {'Authorization': 'Bearer access-one'},
    );
    final responses = [
      dio.get<dynamic>('/private'),
      dio.get<dynamic>('/private'),
    ];
    await Future<void>.delayed(const Duration(milliseconds: 100));
    gate.complete();
    expect(
      (await Future.wait(responses)).map((r) => r.statusCode),
      everyElement(200),
    );
    expect(refresher.calls, 1);
    expect(store.value?.accessToken, 'access-two');
  });

  test('failed refresh clears local credentials', () async {
    final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
    final store = MemoryTokenStore(tokens());
    final adapter = DioAdapter(dio: dio);
    dio.httpClientAdapter = adapter;
    var invalidated = false;
    dio.interceptors.add(
      AuthenticationInterceptor(
        dio,
        store,
        FailingRefresher(),
        onAuthenticationFailed: () => invalidated = true,
      ),
    );
    adapter.onGet(
      '/private',
      (server) => server.reply(401, {'code': 'expired'}),
    );
    await expectLater(
      dio.get<dynamic>('/private'),
      throwsA(isA<DioException>()),
    );
    expect(store.value, isNull);
    expect(store.clearCount, 1);
    expect(invalidated, isTrue);
  });
}
