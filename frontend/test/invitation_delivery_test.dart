import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/core/api/authentication_interceptor.dart';
import 'package:hyped/features/invitations/data/invitation_api.dart';

import 'helpers/fakes.dart';
import 'invitation_api_test.dart' show invitePreviewJson, roomJson;

class _Adapter implements HttpClientAdapter {
  _Adapter(this.respond);
  final ResponseBody Function(RequestOptions) respond;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options.copyWith(headers: Map.of(options.headers)));
    return respond(options);
  }

  @override
  void close({bool force = false}) {}
}

ResponseBody _json(int status, Object body) => ResponseBody.fromString(
  jsonEncode(body),
  status,
  headers: {
    Headers.contentTypeHeader: [Headers.jsonContentType],
  },
);

void main() {
  test(
    'public preview provider has no app interceptors or authorization',
    () async {
      final container = ProviderContainer();
      addTearDown(container.dispose);
      final public = container.read(publicDioProvider);
      final authenticated = container.read(authenticatedDioProvider);
      expect(
        public.interceptors.whereType<AuthenticationInterceptor>(),
        isEmpty,
      );
      // Dio adds its own ImplyContentTypeInterceptor; no app interceptors exist.
      expect(public.interceptors.length, Dio().interceptors.length);
      expect(
        authenticated.interceptors.whereType<AuthenticationInterceptor>(),
        hasLength(1),
      );
      final adapter = _Adapter((_) => _json(200, invitePreviewJson()));
      public.httpClientAdapter = adapter;
      await InvitationApi(
        public,
        authenticated,
      ).previewInvitation('private-token');
      expect(adapter.requests.single.headers['Authorization'], isNull);
      expect(adapter.requests.single.queryParameters, isEmpty);
    },
  );

  for (final roomCode in [false, true]) {
    final channel = roomCode ? 'room code' : 'invite';
    test(
      '$channel joins keep UUID v4 and body through refresh; new attempt gets new key',
      () async {
        final dio = Dio(BaseOptions(baseUrl: 'https://api.test/api/v1'));
        final store = MemoryTokenStore(tokens());
        final refresher = FakeRefresher(tokens('two'));
        dio.interceptors.add(AuthenticationInterceptor(dio, store, refresher));
        final adapter = _Adapter(
          (request) => request.headers['Authorization'] == 'Bearer access-one'
              ? _json(401, {})
              : _json(201, roomJson()),
        );
        dio.httpClientAdapter = adapter;
        final api = InvitationApi(Dio(), dio);
        final join = roomCode ? api.joinRoomCode : api.joinInvitation;
        expect((await join('opaque-preview')).created, isTrue);
        expect((await join('opaque-preview')).created, isTrue);
        expect(refresher.calls, 1);
        expect(adapter.requests, hasLength(3));
        final keys = adapter.requests
            .map((request) => request.headers['Idempotency-Key'])
            .toList();
        expect(
          keys[0],
          matches(
            RegExp(
              r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
            ),
          ),
        );
        expect(keys[1], keys[0]);
        expect(keys[2], isNot(keys[0]));
        for (final request in adapter.requests) {
          expect(
            request.path,
            roomCode ? '/room-codes/join' : '/invitations/join',
          );
          expect(request.data, {'previewReference': 'opaque-preview'});
          expect(request.queryParameters, isEmpty);
        }
      },
    );

    test(
      '$channel failed refresh invalidates session and returns safe unauthorized error',
      () async {
        final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
        final store = MemoryTokenStore(tokens());
        var invalidated = false;
        dio.interceptors.add(
          AuthenticationInterceptor(
            dio,
            store,
            FailingRefresher(),
            onAuthenticationFailed: () => invalidated = true,
          ),
        );
        dio.httpClientAdapter = _Adapter(
          (_) => _json(401, {'message': 'secret-token 7K4MP9QX'}),
        );
        final api = InvitationApi(Dio(), dio);
        await expectLater(
          roomCode
              ? api.joinRoomCode('opaque-preview')
              : api.joinInvitation('opaque-preview'),
          throwsA(
            isA<InvitationApiException>()
                .having((error) => error.statusCode, 'unauthorized', 401)
                .having(
                  (error) => error.toString(),
                  'safe message',
                  'Invitation request failed',
                ),
          ),
        );
        expect(store.value, isNull);
        expect(invalidated, isTrue);
      },
    );

    for (final failure in {
      'INVITATION_INVALID': 410,
      'PREVIEW_EXPIRED': 410,
      'ROOM_FULL': 409,
      'JOINED_ROOM_LIMIT_REACHED': 409,
      'ROOM_ENDED': 410,
    }.entries) {
      test(
        '$channel preserves backend ${failure.key} status and code',
        () async {
          final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
          dio.httpClientAdapter = _Adapter(
            (_) => _json(failure.value, {
              'code': failure.key,
              'message': 'secret-token 7K4MP9QX',
            }),
          );
          final api = InvitationApi(dio, dio);
          await expectLater(
            roomCode
                ? api.joinRoomCode('opaque-preview')
                : api.joinInvitation('opaque-preview'),
            throwsA(
              isA<InvitationApiException>()
                  .having((error) => error.statusCode, 'status', failure.value)
                  .having((error) => error.code, 'code', failure.key)
                  .having(
                    (error) => error.toString(),
                    'safe message',
                    'Invitation request failed',
                  ),
            ),
          );
        },
      );
    }

    for (final type in [
      DioExceptionType.connectionError,
      DioExceptionType.connectionTimeout,
      DioExceptionType.receiveTimeout,
    ]) {
      test('$channel sanitizes $type in preview and join', () async {
        final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
        dio.httpClientAdapter = _Adapter(
          (request) => throw DioException(
            requestOptions: request,
            type: type,
            message: 'secret-token 7K4MP9QX',
          ),
        );
        final api = InvitationApi(dio, dio);
        for (final operation in [
          () => roomCode
              ? api.previewRoomCode('7K4MP9QX')
              : api.previewInvitation('secret-token'),
          () => roomCode
              ? api.joinRoomCode('opaque-preview')
              : api.joinInvitation('opaque-preview'),
        ]) {
          await expectLater(
            operation(),
            throwsA(
              isA<InvitationApiException>().having(
                (error) => error.toString(),
                'safe message',
                'Invitation request failed',
              ),
            ),
          );
        }
      });
    }
  }

  for (final body in [
    {'code': 'secret-token 7K4MP9QX'},
    {
      'code': ['secret-token'],
    },
    'secret-token 7K4MP9QX',
  ]) {
    test('untrusted backend error is sanitized: ${body.runtimeType}', () async {
      final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
      dio.httpClientAdapter = _Adapter((_) => _json(500, body));
      await expectLater(
        InvitationApi(dio, dio).previewInvitation('secret-token'),
        throwsA(
          isA<InvitationApiException>()
              .having((error) => error.code, 'unknown code discarded', isNull)
              .having((error) => error.statusCode, 'status', 500)
              .having(
                (error) => error.toString(),
                'safe message',
                'Invitation request failed',
              ),
        ),
      );
    });
  }

  test(
    'malformed success payload does not expose parsing diagnostics',
    () async {
      final dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
      dio.httpClientAdapter = _Adapter(
        (_) => _json(200, {
          ...invitePreviewJson(),
          'expiresAt': 'secret-token 7K4MP9QX',
        }),
      );
      await expectLater(
        InvitationApi(dio, dio).previewInvitation('secret-token'),
        throwsA(
          isA<InvitationApiException>().having(
            (error) => error.toString(),
            'safe message',
            'Invitation request failed',
          ),
        ),
      );
    },
  );
}
