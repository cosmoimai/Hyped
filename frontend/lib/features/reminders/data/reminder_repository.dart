import 'package:hyped/features/reminders/data/reminder_api.dart';
import 'package:hyped/features/reminders/domain/reminder_preferences.dart';

class ReminderRepository {
  ReminderRepository(this._api);

  final ReminderApi _api;

  Future<ReminderPreferences> get(String roomId) => _api.get(roomId);

  Future<ReminderPreferences> replace(
    String roomId,
    ReminderPreferences preferences,
  ) => _api.replace(roomId, preferences);
}
