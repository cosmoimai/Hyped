import 'package:dio/dio.dart';
import 'package:hyped/features/invitations/domain/invitation.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:uuid/uuid.dart';

class InvitationApi {
  InvitationApi(this._publicDio, this._authenticatedDio);

  final Dio _publicDio;
  final Dio _authenticatedDio;

  Future<InvitePreview> previewInvitation(String token) async {
    try {
      final response = await _publicDio.post<Map<String, dynamic>>(
        '/public/invitations/preview',
        data: {'token': token},
      );
      return InvitePreview.fromJson(response.data!);
    } on DioException catch (error) {
      throw InvitationApiException.from(error);
    }
  }

  Future<InvitePreview> previewRoomCode(String roomCode) async {
    try {
      final response = await _publicDio.post<Map<String, dynamic>>(
        '/public/room-codes/preview',
        data: {'roomCode': roomCode},
      );
      return InvitePreview.fromJson(response.data!);
    } on DioException catch (error) {
      throw InvitationApiException.from(error);
    }
  }

  Future<InviteJoinResult> joinInvitation(String previewReference) =>
      _join('/invitations/join', previewReference);

  Future<InviteJoinResult> joinRoomCode(String previewReference) =>
      _join('/room-codes/join', previewReference);

  Future<InviteJoinResult> _join(String path, String previewReference) async {
    try {
      final response = await _authenticatedDio.post<Map<String, dynamic>>(
        path,
        data: {'previewReference': previewReference},
        options: Options(headers: {'Idempotency-Key': const Uuid().v4()}),
      );
      return InviteJoinResult(
        room: Room.fromJson(response.data!),
        created: response.statusCode == 201,
      );
    } on DioException catch (error) {
      throw InvitationApiException.from(error);
    }
  }
}

class InvitationApiException implements Exception {
  const InvitationApiException({this.code, this.statusCode});

  final String? code;
  final int? statusCode;

  factory InvitationApiException.from(DioException error) {
    final data = error.response?.data;
    String? code;
    if (data is Map<String, dynamic>) code = data['code'] as String?;
    return InvitationApiException(
      code: code,
      statusCode: error.response?.statusCode,
    );
  }

  @override
  String toString() =>
      'Invitation request failed${code == null ? '' : ': $code'}';
}
