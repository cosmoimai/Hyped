import 'package:dio/dio.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_commands.dart';
import 'package:uuid/uuid.dart';

class RoomApi {
  RoomApi(this._dio);

  final Dio _dio;

  Future<List<Room>> listRooms({RoomStatus? status, int limit = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/rooms',
        queryParameters: {
          if (status != null) 'status': status.wire,
          'limit': limit,
        },
      );
      final items = response.data?['items'] as List<dynamic>? ?? const [];
      return items
          .whereType<Map>()
          .map((item) => Room.fromJson(Map<String, dynamic>.from(item)))
          .toList(growable: false);
    } on DioException catch (error) {
      throw RoomApiException.from(error);
    }
  }

  Future<Room> getRoom(String roomId) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/rooms/$roomId');
      return Room.fromJson(
        response.data!,
        etag: response.headers.value('etag'),
      );
    } on DioException catch (error) {
      throw RoomApiException.from(error);
    }
  }

  Future<RoomEventTheme> getEventTheme(String roomId) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/rooms/$roomId/event-theme',
      );
      return RoomEventTheme.fromJson(
        response.data!,
        etag: response.headers.value('etag'),
      );
    } on DioException catch (error) {
      throw RoomApiException.from(error);
    }
  }

  Future<Room> createRoom(RoomDraft draft) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/rooms',
        data: draft.toCreateJson(),
        options: Options(headers: {'Idempotency-Key': const Uuid().v4()}),
      );
      return Room.fromJson(
        response.data!,
        etag: response.headers.value('etag'),
      );
    } on DioException catch (error) {
      throw RoomApiException.from(error);
    }
  }

  Future<Room> updateRoom({
    required String roomId,
    required String etag,
    required RoomDraft draft,
  }) async {
    try {
      final response = await _dio.patch<Map<String, dynamic>>(
        '/rooms/$roomId',
        data: draft.toPatchJson(),
        options: Options(
          contentType: 'application/merge-patch+json',
          headers: {'If-Match': etag},
        ),
      );
      return Room.fromJson(
        response.data!,
        etag: response.headers.value('etag'),
      );
    } on DioException catch (error) {
      throw RoomApiException.from(error);
    }
  }

  Future<RoomEventTheme> updateEventTheme({
    required String roomId,
    required String etag,
    required RoomDraft draft,
  }) async {
    try {
      final response = await _dio.patch<Map<String, dynamic>>(
        '/rooms/$roomId/event-theme',
        data: draft.toPatchJson(),
        options: Options(
          contentType: 'application/merge-patch+json',
          headers: {'If-Match': etag},
        ),
      );
      return RoomEventTheme.fromJson(
        response.data!,
        etag: response.headers.value('etag'),
      );
    } on DioException catch (error) {
      throw RoomApiException.from(error);
    }
  }
}

class RoomApiException implements Exception {
  const RoomApiException({this.code, this.statusCode});

  final String? code;
  final int? statusCode;

  factory RoomApiException.from(DioException error) {
    final data = error.response?.data;
    String? code;
    if (data is Map<String, dynamic>) code = data['code'] as String?;
    return RoomApiException(code: code, statusCode: error.response?.statusCode);
  }

  @override
  String toString() => 'Room request failed${code == null ? '' : ': $code'}';
}
