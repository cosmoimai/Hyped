import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:hyped/core/api/integrity_interceptor.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_commands.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_theme_view.dart';

void main() {
  const formThemeContract = [
    {'kind': 'PRESET', 'presetKey': 'soft-blue-01', 'overlayKey': 'dark-soft'},
    {'kind': 'PRESET', 'presetKey': 'mint-sky-01', 'overlayKey': 'dark-soft'},
    {
      'kind': 'GRADIENT',
      'presetKey': 'blue-lilac-02',
      'overlayKey': 'dark-soft',
    },
    {
      'kind': 'GRADIENT',
      'presetKey': 'peach-gold-01',
      'overlayKey': 'dark-soft',
    },
  ];
  for (var index = 0; index < formThemeContract.length; index++) {
    test('form theme $index uses backend keys for create and edit', () async {
      final expectedTheme = formThemeContract[index];
      final dio = Dio(BaseOptions(baseUrl: 'https://api.test/api/v1'));
      final adapter = DioAdapter(dio: dio);
      dio.httpClientAdapter = adapter;
      final draft = RoomDraft(
        title: 'Theme contract',
        eventDate: DateTime(2030, 12, 20),
        eventTime: (hour: 19, minute: 0),
        eventTimeZone: 'Asia/Kolkata',
        theme: roomThemeChoices[index],
      );
      final response = {...roomJson(), 'theme': expectedTheme};
      adapter.onPost(
        '/rooms',
        (server) => server.reply(201, response),
        data: {...draft.toCreateJson(), 'theme': expectedTheme},
      );
      adapter.onPatch(
        '/rooms/theme-room',
        (server) => server.reply(200, response),
        data: {...draft.toPatchJson(), 'theme': expectedTheme},
        headers: {'If-Match': '"room-4"'},
      );
      final api = RoomApi(dio);
      expect((await api.createRoom(draft)).theme.toJson(), expectedTheme);
      expect(
        (await api.updateRoom(
          roomId: 'theme-room',
          etag: '"room-4"',
          draft: draft,
        )).theme.toJson(),
        expectedTheme,
      );
    });
  }

  test(
    'attested room edit preserves merge-patch type, revision and new event time',
    () async {
      final dio = Dio(
        BaseOptions(
          baseUrl: 'https://api.test/api/v1',
          headers: {'Authorization': 'Bearer fixture-access'},
        ),
      );
      final adapter = DioAdapter(dio: dio);
      dio.interceptors.add(IntegrityInterceptor((_) async => null));
      RequestOptions? seen;
      dio.interceptors.add(
        InterceptorsWrapper(
          onRequest: (options, handler) {
            seen = options;
            handler.next(options);
          },
        ),
      );
      final draft = RoomDraft(
        title: 'Updated event',
        eventDate: DateTime(2030, 12, 20),
        eventTime: (hour: 10, minute: 10),
        eventTimeZone: 'Asia/Kolkata',
        theme: roomThemeChoices.first,
      );
      adapter.onPatch(
        '/rooms/fixture-room',
        (server) => server.reply(200, {
          ...roomJson(),
          'eventAt': '2030-12-20T04:40:00Z',
          'revision': 5,
        }),
        data: jsonEncode(draft.toPatchJson()),
      );
      final updated = await RoomApi(
        dio,
      ).updateRoom(roomId: 'fixture-room', etag: '"room-4"', draft: draft);
      expect(seen!.contentType, 'application/merge-patch+json');
      expect(seen!.headers['If-Match'], '"room-4"');
      expect(jsonDecode(seen!.data as String)['eventLocalTime'], '10:10');
      expect(updated.eventAt, DateTime.utc(2030, 12, 20, 4, 40));
      expect(updated.revision, 5);
    },
  );

  test('room models parse backend response fields', () {
    final room = Room.fromJson(roomJson(), etag: '"room-4"');

    expect(room.id, '019b1f33-e664-7ef4-985e-76b3ac298620');
    expect(room.status, RoomStatus.active);
    expect(room.role, RoomRole.owner);
    expect(room.canEdit, isTrue);
    expect(room.theme.kind, RoomThemeKind.preset);
    expect(room.etag, '"room-4"');
  });

  test('client lists, creates, reads detail and updates with ETag', () async {
    final dio = Dio(BaseOptions(baseUrl: 'https://api.test/api/v1'));
    final adapter = DioAdapter(dio: dio);
    dio.httpClientAdapter = adapter;
    final api = RoomApi(dio);
    final draft = RoomDraft(
      title: 'Goa trip',
      eventDate: DateTime(2030, 12, 20),
      eventTime: (hour: 10, minute: 0),
      eventTimeZone: 'Asia/Kolkata',
      location: 'North Goa',
      description: 'Our first group trip',
      theme: const RoomTheme(
        kind: RoomThemeKind.preset,
        presetKey: 'soft-blue-01',
        overlayKey: 'dark-soft',
      ),
    );

    adapter.onGet(
      '/rooms',
      (server) => server.reply(200, {
        'items': [roomJson()],
        'nextCursor': null,
      }),
      queryParameters: {'status': 'ACTIVE', 'limit': 20},
    );
    adapter.onPost(
      '/rooms',
      (server) => server.reply(201, roomJson()),
      data: draft.toCreateJson(),
    );
    adapter.onGet(
      '/rooms/019b1f33-e664-7ef4-985e-76b3ac298620',
      (server) => server.reply(200, roomJson()),
    );
    adapter.onPatch(
      '/rooms/019b1f33-e664-7ef4-985e-76b3ac298620',
      (server) => server.reply(200, {...roomJson(), 'revision': 5}),
      data: draft.toPatchJson(),
      headers: {'If-Match': '"room-4"'},
    );
    adapter.onGet(
      '/rooms/019b1f33-e664-7ef4-985e-76b3ac298620/event-theme',
      (server) => server.reply(200, eventThemeJson()),
    );

    expect(await api.listRooms(status: RoomStatus.active), hasLength(1));
    expect((await api.createRoom(draft)).title, 'Goa trip');
    expect(
      (await api.getRoom('019b1f33-e664-7ef4-985e-76b3ac298620')).memberCount,
      8,
    );
    expect(
      (await api.updateRoom(
        roomId: '019b1f33-e664-7ef4-985e-76b3ac298620',
        etag: '"room-4"',
        draft: draft,
      )).revision,
      5,
    );
    expect(
      (await api.getEventTheme(
        '019b1f33-e664-7ef4-985e-76b3ac298620',
      )).revision,
      4,
    );
  });

  test('API errors map to safe room exception', () async {
    final dio = Dio(BaseOptions(baseUrl: 'https://api.test/api/v1'));
    final adapter = DioAdapter(dio: dio);
    dio.httpClientAdapter = adapter;
    adapter.onGet(
      '/rooms/bad',
      (server) => server.reply(404, {'code': 'ROOM_UNAVAILABLE'}),
    );

    await expectLater(
      RoomApi(dio).getRoom('bad'),
      throwsA(
        isA<RoomApiException>().having(
          (error) => error.code,
          'code',
          'ROOM_UNAVAILABLE',
        ),
      ),
    );
  });
}

Map<String, dynamic> roomJson() => {
  'id': '019b1f33-e664-7ef4-985e-76b3ac298620',
  'title': 'Goa trip',
  'eventAt': '2030-12-20T04:30:00Z',
  'eventTimeZone': 'Asia/Kolkata',
  'location': 'North Goa',
  'description': 'Our first group trip',
  'theme': {
    'kind': 'PRESET',
    'presetKey': 'soft-blue-01',
    'overlayKey': 'dark-soft',
  },
  'status': 'ACTIVE',
  'role': 'OWNER',
  'memberCount': 8,
  'revision': 4,
  'archivedAt': null,
  'deleteAfter': null,
  'createdAt': '2026-09-09T10:00:00Z',
  'updatedAt': '2026-09-09T10:30:00Z',
  'serverNow': '2026-09-09T11:00:00Z',
};

Map<String, dynamic> eventThemeJson() => {
  'id': '019b1f33-e664-7ef4-985e-76b3ac298620',
  'title': 'Goa trip',
  'eventAt': '2030-12-20T04:30:00Z',
  'eventTimeZone': 'Asia/Kolkata',
  'location': 'North Goa',
  'description': 'Our first group trip',
  'theme': {
    'kind': 'PRESET',
    'presetKey': 'soft-blue-01',
    'overlayKey': 'dark-soft',
  },
  'revision': 4,
  'updatedAt': '2026-09-09T10:30:00Z',
  'serverNow': '2026-09-09T11:00:00Z',
};
