import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:hyped/features/invitations/data/invitation_api.dart';
import 'package:hyped/features/invitations/domain/invitation.dart';
import 'package:hyped/features/rooms/domain/room.dart';

void main() {
  test(
    'preview model parses documented public fields without owner identity',
    () {
      final preview = InvitePreview.fromJson(invitePreviewJson());

      expect(preview.previewReference, 'preview-ref');
      expect(preview.event.title, 'Goa trip');
      expect(preview.event.theme.overlayKey, 'dark-soft');
      expect(preview.inviter.displayName, 'Aarav');
      expect(preview.memberCount, 8);
      expect(preview.toString(), 'InvitePreview[REDACTED]');
    },
  );

  test(
    'client uses public Dio for previews and authenticated Dio for joins',
    () async {
      final publicDio = Dio(BaseOptions(baseUrl: 'https://api.test/api/v1'));
      final authenticatedDio = Dio(
        BaseOptions(baseUrl: 'https://api.test/api/v1'),
      );
      final publicAdapter = DioAdapter(dio: publicDio);
      final authenticatedAdapter = DioAdapter(dio: authenticatedDio);
      publicDio.httpClientAdapter = publicAdapter;
      authenticatedDio.httpClientAdapter = authenticatedAdapter;
      authenticatedDio.interceptors.add(
        InterceptorsWrapper(
          onRequest: (options, handler) {
            options.headers['Authorization'] = 'Bearer access-token';
            handler.next(options);
          },
        ),
      );
      final api = InvitationApi(publicDio, authenticatedDio);

      publicAdapter.onPost(
        '/public/invitations/preview',
        (server) => server.reply(200, invitePreviewJson()),
        data: {'token': 'opaque-token'},
      );
      publicAdapter.onPost(
        '/public/room-codes/preview',
        (server) => server.reply(200, invitePreviewJson()),
        data: {'roomCode': '7K4MP9QX'},
      );
      authenticatedAdapter.onPost(
        '/invitations/join',
        (server) => server.reply(201, roomJson()),
        data: {'previewReference': 'preview-ref'},
        headers: {'Authorization': 'Bearer access-token'},
      );
      authenticatedAdapter.onPost(
        '/room-codes/join',
        (server) => server.reply(200, roomJson()),
        data: {'previewReference': 'preview-ref'},
        headers: {'Authorization': 'Bearer access-token'},
      );

      expect(
        (await api.previewInvitation('opaque-token')).event.title,
        'Goa trip',
      );
      expect((await api.previewRoomCode('7K4MP9QX')).memberCount, 8);
      expect((await api.joinInvitation('preview-ref')).created, isTrue);
      expect((await api.joinRoomCode('preview-ref')).created, isFalse);
    },
  );

  test('API errors expose stable codes without credentials', () async {
    final dio = Dio(BaseOptions(baseUrl: 'https://api.test/api/v1'));
    final adapter = DioAdapter(dio: dio);
    dio.httpClientAdapter = adapter;
    adapter.onPost(
      '/public/invitations/preview',
      (server) => server.reply(410, {'code': 'INVITATION_INVALID'}),
      data: {'token': 'secret-token'},
    );

    await expectLater(
      InvitationApi(dio, Dio()).previewInvitation('secret-token'),
      throwsA(
        isA<InvitationApiException>()
            .having((error) => error.code, 'code', 'INVITATION_INVALID')
            .having(
              (error) => error.toString(),
              'safe text',
              isNot(contains('secret-token')),
            ),
      ),
    );
  });
}

Map<String, dynamic> invitePreviewJson() => {
  'previewReference': 'preview-ref',
  'expiresAt': '2026-09-09T10:40:00Z',
  'event': {
    'title': 'Goa trip',
    'eventAt': '2030-12-20T04:30:00Z',
    'eventTimeZone': 'Asia/Kolkata',
    'theme': {'kind': 'PRESET', 'presetKey': 'soft-blue-01'},
  },
  'inviter': {'displayName': 'Aarav'},
  'memberCount': 8,
  'requiresAuthentication': true,
};

Map<String, dynamic> roomJson() => {
  'id': 'room-id',
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
  'role': RoomRole.member.wire,
  'memberCount': 8,
  'revision': 4,
  'archivedAt': null,
  'deleteAfter': null,
  'createdAt': '2026-09-09T10:00:00Z',
  'updatedAt': '2026-09-09T10:30:00Z',
  'serverNow': '2026-09-09T11:00:00Z',
};
