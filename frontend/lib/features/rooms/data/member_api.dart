import 'package:dio/dio.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_member.dart';

class MemberApi {
  MemberApi(this.dio);
  final Dio dio;
  Future<MemberSnapshot> load(String roomId) async {
    try {
      final response = await dio.get<Map<String, dynamic>>(
        '/rooms/$roomId/members',
      );
      final data = response.data!;
      return MemberSnapshot(
        (data['items'] as List)
            .map(
              (item) =>
                  RoomMember.fromJson(Map<String, dynamic>.from(item as Map)),
            )
            .toList(),
        RoomRoleX.fromWire(data['callerRole'] as String),
        data['status'] == 'ACTIVE',
        response.headers.value('etag') ?? '"room-${data['revision']}"',
      );
    } on DioException catch (error) {
      throw RoomApiException.from(error);
    }
  }

  Future<void> change(
    String roomId,
    String userId,
    MemberAction action,
    String etag,
  ) async {
    final options = Options(headers: {'If-Match': etag});
    try {
      switch (action) {
        case MemberAction.promote:
        case MemberAction.demote:
          await dio.patch<void>(
            '/rooms/$roomId/members/$userId',
            data: {
              'role': action == MemberAction.promote ? 'CO_HOST' : 'MEMBER',
            },
            options: options,
          );
        case MemberAction.remove:
          await dio.delete<void>(
            '/rooms/$roomId/members/$userId',
            options: options,
          );
        case MemberAction.transfer:
          await dio.post<void>(
            '/rooms/$roomId/ownership-transfer',
            data: {'newOwnerUserId': userId},
            options: options,
          );
      }
    } on DioException catch (error) {
      throw RoomApiException.from(error);
    }
  }
}
