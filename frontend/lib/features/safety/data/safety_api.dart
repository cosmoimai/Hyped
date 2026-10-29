import 'package:dio/dio.dart';
import 'package:hyped/features/rooms/data/room_api.dart';

class BlockedAccount {
  const BlockedAccount(this.userId, this.displayName);
  final String userId;
  final String displayName;
  @override
  String toString() => 'BlockedAccount[REDACTED]';
}

class SafetyApi {
  SafetyApi(this.dio);
  final Dio dio;
  Options _options(String session, [String? key]) => Options(
    extra: {'expectedSessionId': session},
    headers: {'Idempotency-Key': ?key},
  );
  Future<void> report({
    required String roomId,
    required String subjectType,
    String? subjectUserId,
    required String reason,
    String? details,
    required String session,
    required String key,
  }) async {
    try {
      await dio.post<void>(
        '/reports',
        options: _options(session, key),
        data: {
          'roomId': roomId,
          'subjectType': subjectType,
          'subjectUserId': ?subjectUserId,
          'reasonCode': reason,
          if (details != null && details.trim().isNotEmpty)
            'details': details.trim(),
        },
      );
    } on DioException catch (error) {
      throw RoomApiException.from(error);
    }
  }

  Future<void> block(
    String target,
    String room,
    String session,
    String key,
  ) async {
    try {
      await dio.put<void>(
        '/me/blocks/$target',
        data: {'roomId': room},
        options: _options(session, key),
      );
    } on DioException catch (error) {
      throw RoomApiException.from(error);
    }
  }

  Future<void> unblock(String target, String session) async {
    try {
      await dio.delete<void>('/me/blocks/$target', options: _options(session));
    } on DioException catch (error) {
      throw RoomApiException.from(error);
    }
  }

  Future<List<BlockedAccount>> blocks(String session) async {
    try {
      final items = <BlockedAccount>[];
      String? after;
      final seen = <String>{};
      do {
        final response = await dio.get<Map<String, dynamic>>(
          '/me/blocks',
          options: _options(session),
          queryParameters: {'after': ?after},
        );
        items.addAll(
          (response.data!['items'] as List).map(
            (item) => BlockedAccount(
              item['userId'] as String,
              item['displayName'] as String? ?? 'Blocked account',
            ),
          ),
        );
        after = response.data!['nextCursor'] as String?;
        if (after != null && !seen.add(after)) {
          throw const RoomApiException(code: 'INVALID_RESPONSE');
        }
      } while (after != null);
      return items;
    } on DioException catch (error) {
      throw RoomApiException.from(error);
    }
  }
}
