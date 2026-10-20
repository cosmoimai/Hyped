import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';
import 'package:hyped/features/reminders/data/reminder_api.dart';
import 'package:hyped/features/reminders/domain/reminder_preferences.dart';

void main() {
  test(
    'reminder API reads and replaces the documented three toggles',
    () async {
      final dio = Dio(BaseOptions(baseUrl: 'https://api.test/api/v1'));
      final adapter = DioAdapter(dio: dio);
      dio.httpClientAdapter = adapter;
      final value = {
        'remind24HoursBefore': true,
        'remind1HourBefore': false,
        'remindAtEventTime': true,
        'updatedAt': '2026-10-20T10:00:00Z',
      };
      adapter.onGet(
        '/rooms/room-id/reminder-preferences',
        (server) => server.reply(200, value),
      );
      adapter.onPut(
        '/rooms/room-id/reminder-preferences',
        (server) => server.reply(200, value),
        data: {
          'remind24HoursBefore': true,
          'remind1HourBefore': false,
          'remindAtEventTime': true,
        },
      );
      final api = ReminderApi(dio);

      final loaded = await api.get('room-id');
      final replaced = await api.replace(
        'room-id',
        ReminderPreferences(
          remind24HoursBefore: true,
          remind1HourBefore: false,
          remindAtEventTime: true,
          updatedAt: DateTime.utc(2026, 10, 20, 10),
        ),
      );

      expect(loaded.remind1HourBefore, isFalse);
      expect(replaced.remindAtEventTime, isTrue);
    },
  );

  test(
    'reminder API maps backend errors without exposing request details',
    () async {
      final dio = Dio(BaseOptions(baseUrl: 'https://api.test/api/v1'));
      final adapter = DioAdapter(dio: dio);
      dio.httpClientAdapter = adapter;
      adapter.onGet(
        '/rooms/room-id/reminder-preferences',
        (server) => server.reply(403, {'code': 'ROOM_ACTION_FORBIDDEN'}),
      );

      await expectLater(
        ReminderApi(dio).get('room-id'),
        throwsA(
          isA<ReminderApiException>().having(
            (error) => error.code,
            'code',
            'ROOM_ACTION_FORBIDDEN',
          ),
        ),
      );
    },
  );
}
