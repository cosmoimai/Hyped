import 'package:dio/dio.dart';
import 'package:hyped/features/reminders/domain/reminder_preferences.dart';

class ReminderApi {
  ReminderApi(this._dio);

  final Dio _dio;

  Future<ReminderPreferences> get(String roomId) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/rooms/$roomId/reminder-preferences',
      );
      return ReminderPreferences.fromJson(response.data!);
    } on DioException catch (error) {
      throw ReminderApiException.from(error);
    }
  }

  Future<ReminderPreferences> replace(
    String roomId,
    ReminderPreferences preferences,
  ) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>(
        '/rooms/$roomId/reminder-preferences',
        data: preferences.toJson(),
      );
      return ReminderPreferences.fromJson(response.data!);
    } on DioException catch (error) {
      throw ReminderApiException.from(error);
    }
  }
}

class ReminderApiException implements Exception {
  const ReminderApiException({this.code, this.statusCode});

  final String? code;
  final int? statusCode;

  factory ReminderApiException.from(DioException error) {
    final data = error.response?.data;
    return ReminderApiException(
      code: data is Map<String, dynamic> ? data['code'] as String? : null,
      statusCode: error.response?.statusCode,
    );
  }

  @override
  String toString() =>
      'Reminder request failed${code == null ? '' : ': $code'}';
}
