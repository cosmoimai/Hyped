import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/features/reminders/data/reminder_api.dart';
import 'package:hyped/features/reminders/data/reminder_repository.dart';
import 'package:hyped/features/reminders/domain/reminder_preferences.dart';
import 'package:hyped/features/reminders/presentation/controllers/reminder_providers.dart';

void main() {
  test('controller loads defaults and replaces one toggle', () async {
    final repository = _FakeReminderRepository();
    final controller = ReminderController(repository, 'room-id');

    await controller.load();
    expect(controller.preferences?.remind24HoursBefore, isTrue);

    await controller.replace(
      ReminderPreferences(
        remind24HoursBefore: false,
        remind1HourBefore: true,
        remindAtEventTime: true,
        updatedAt: DateTime.utc(2026),
      ),
    );

    expect(repository.value.remind24HoursBefore, isFalse);
    expect(controller.error, isNull);
  });
}

class _FakeReminderRepository extends ReminderRepository {
  _FakeReminderRepository() : super(ReminderApi(Dio()));

  ReminderPreferences value = ReminderPreferences(
    remind24HoursBefore: true,
    remind1HourBefore: true,
    remindAtEventTime: true,
    updatedAt: DateTime.utc(2026),
  );

  @override
  Future<ReminderPreferences> get(String roomId) async => value;

  @override
  Future<ReminderPreferences> replace(
    String roomId,
    ReminderPreferences preferences,
  ) async {
    value = preferences;
    return value;
  }
}
