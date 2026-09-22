import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:hyped/features/authentication/data/auth_api.dart';

void main() {
  test('exchange and refresh match the backend contract', () async {
    final publicDio = Dio(BaseOptions(baseUrl: 'https://api.test'));
    final authenticatedDio = Dio(BaseOptions(baseUrl: 'https://api.test'));
    final adapter = DioAdapter(dio: publicDio);
    publicDio.httpClientAdapter = adapter;
    final api = AuthApi(publicDio, authenticatedDio);
    adapter.onPost(
      '/auth/exchange',
      (server) => server.reply(201, {
        ...tokenJson('one'),
        'user': {
          'id': 'user-id',
          'displayName': 'Hyped User',
          'photo': null,
          'profileRevision': 1,
        },
        'isNewAccount': true,
      }),
      data: {
        'firebaseIdToken': 'firebase-proof',
        'installationId': 'installation-id',
        'platform': 'ANDROID',
        'deviceName': 'Pixel',
        'appVersion': '0.1.0+1',
      },
    );
    adapter.onPost(
      '/auth/refresh',
      (server) => server.reply(200, tokenJson('two')),
      data: {
        'refreshToken': 'refresh-one',
        'installationId': 'installation-id',
      },
    );

    final exchange = await api.exchange(
      firebaseIdToken: 'firebase-proof',
      installationId: 'installation-id',
      deviceName: 'Pixel',
      appVersion: '0.1.0+1',
    );
    final refreshed = await api.refresh('refresh-one', 'installation-id');

    expect(exchange.isNewAccount, isTrue);
    expect(exchange.user.displayName, 'Hyped User');
    expect(exchange.tokens.accessToken, 'access-one');
    expect(refreshed.accessToken, 'access-two');
  });

  test('API failures expose no request credentials', () async {
    final publicDio = Dio(BaseOptions(baseUrl: 'https://api.test'));
    final adapter = DioAdapter(dio: publicDio);
    publicDio.httpClientAdapter = adapter;
    final api = AuthApi(publicDio, Dio());
    adapter.onPost(
      '/auth/refresh',
      (server) => server.reply(401, {}),
      data: {'refreshToken': 'raw-secret', 'installationId': 'installation-id'},
    );

    Object? failure;
    try {
      await api.refresh('raw-secret', 'installation-id');
    } catch (error) {
      failure = error;
    }

    expect(failure, isA<AuthenticationApiException>());
    expect(failure.toString(), isNot(contains('raw-secret')));
  });
}

Map<String, dynamic> tokenJson(String suffix) => {
  'accessToken': 'access-$suffix',
  'accessTokenExpiresAt': '2026-09-21T12:00:00Z',
  'refreshToken': 'refresh-$suffix',
  'refreshTokenExpiresAt': '2026-10-21T12:00:00Z',
  'sessionId': '019b1f21-31ca-749e-b9b9-96d4c0efec40',
};
