import 'package:dio/dio.dart';

class PushApi {
  PushApi(this.dio);
  final Dio dio;
  Future<void> register(
    String installationId,
    String sessionId,
    String token,
    String version,
  ) async {
    try {
      await dio.put<void>(
        '/devices/$installationId',
        data: {
          'platform': 'ANDROID',
          'fcmToken': token,
          'notificationsEnabled': true,
          'appVersion': version,
        },
        options: Options(extra: {'expectedSessionId': sessionId}),
      );
    } catch (_) {
      throw const PushApiException();
    }
  }

  Future<void> remove(String installationId, String sessionId) async {
    try {
      await dio.delete<void>(
        '/devices/$installationId',
        options: Options(extra: {'expectedSessionId': sessionId}),
      );
    } catch (_) {
      throw const PushApiException();
    }
  }
}

class PushApiException implements Exception {
  const PushApiException();
  @override
  String toString() => 'Notification registration unavailable';
}
