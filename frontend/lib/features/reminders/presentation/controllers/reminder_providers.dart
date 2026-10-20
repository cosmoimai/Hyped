import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/reminders/data/reminder_api.dart';
import 'package:hyped/features/reminders/data/reminder_repository.dart';
import 'package:hyped/features/reminders/domain/reminder_preferences.dart';

final reminderApiProvider = Provider<ReminderApi>(
  (ref) => ReminderApi(ref.read(authenticatedDioProvider)),
);

final reminderRepositoryProvider = Provider<ReminderRepository>(
  (ref) => ReminderRepository(ref.read(reminderApiProvider)),
);

final reminderControllerProvider =
    ChangeNotifierProvider.family<ReminderController, String>((ref, roomId) {
      final controller = ReminderController(
        ref.read(reminderRepositoryProvider),
        roomId,
      );
      controller.load();
      return controller;
    });

class ReminderController extends ChangeNotifier {
  ReminderController(this._repository, this.roomId);

  final ReminderRepository _repository;
  final String roomId;
  ReminderPreferences? _preferences;
  bool _loading = false;
  bool _saving = false;
  String? _error;

  ReminderPreferences? get preferences => _preferences;
  bool get loading => _loading;
  bool get saving => _saving;
  String? get error => _error;

  Future<void> load() async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      _preferences = await _repository.get(roomId);
    } on ReminderApiException catch (error) {
      _error = _message(error);
    } catch (_) {
      _error = 'Could not load reminder settings.';
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  Future<void> replace(ReminderPreferences next) async {
    _saving = true;
    _error = null;
    notifyListeners();
    try {
      _preferences = await _repository.replace(roomId, next);
    } on ReminderApiException catch (error) {
      _error = _message(error);
    } catch (_) {
      _error = 'Could not save reminder settings.';
    } finally {
      _saving = false;
      notifyListeners();
    }
  }
}

String _message(ReminderApiException error) => switch (error.code) {
  'ROOM_UNAVAILABLE' || 'ROOM_ARCHIVED' => 'This room is no longer active.',
  'ROOM_ACTION_FORBIDDEN' => 'You no longer have access to this room.',
  _ => 'Could not update reminder settings. Please try again.',
};
