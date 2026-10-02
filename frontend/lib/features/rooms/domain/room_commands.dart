import 'package:hyped/features/rooms/domain/room.dart';

class RoomDraft {
  const RoomDraft({
    required this.title,
    required this.eventDate,
    required this.eventTime,
    required this.eventTimeZone,
    required this.theme,
    this.location,
    this.description,
  });

  final String title;
  final DateTime eventDate;
  final ({int hour, int minute}) eventTime;
  final String eventTimeZone;
  final String? location;
  final String? description;
  final RoomTheme theme;

  Map<String, dynamic> toCreateJson() {
    final json = <String, dynamic>{
      'title': title.trim(),
      'eventLocalDate': _date(eventDate),
      'eventLocalTime': _time(eventTime),
      'eventTimeZone': eventTimeZone,
      'theme': theme.toJson(),
    };
    final selectedLocation = _optional(location);
    if (selectedLocation != null) json['location'] = selectedLocation;
    final selectedDescription = _optional(description);
    if (selectedDescription != null) json['description'] = selectedDescription;
    return json;
  }

  Map<String, dynamic> toPatchJson() => {
    'title': title.trim(),
    'eventLocalDate': _date(eventDate),
    'eventLocalTime': _time(eventTime),
    'eventTimeZone': eventTimeZone,
    'location': _optional(location),
    'description': _optional(description),
    'theme': theme.toJson(),
  };
}

class RoomDraftValidation {
  const RoomDraftValidation._();

  static String? validate(RoomDraft draft, DateTime now) {
    final title = draft.title.trim();
    if (title.isEmpty) return 'Add a title.';
    if (title.length > 80) return 'Title must be 80 characters or fewer.';
    final location = draft.location?.trim();
    if (location != null && location.length > 120) {
      return 'Location must be 120 characters or fewer.';
    }
    final description = draft.description?.trim();
    if (description != null && description.length > 500) {
      return 'Description must be 500 characters or fewer.';
    }
    final local = DateTime(
      draft.eventDate.year,
      draft.eventDate.month,
      draft.eventDate.day,
      draft.eventTime.hour,
      draft.eventTime.minute,
    );
    if (draft.eventTimeZone == 'UTC' && !local.toUtc().isAfter(now.toUtc())) {
      return 'Event time must be in the future.';
    }
    return null;
  }
}

String _date(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')}';

String _time(({int hour, int minute}) value) =>
    '${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}';

String? _optional(String? value) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? null : trimmed;
}
