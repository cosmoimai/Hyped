class ReminderPreferences {
  const ReminderPreferences({
    required this.remind24HoursBefore,
    required this.remind1HourBefore,
    required this.remindAtEventTime,
    required this.updatedAt,
  });

  final bool remind24HoursBefore;
  final bool remind1HourBefore;
  final bool remindAtEventTime;
  final DateTime updatedAt;

  factory ReminderPreferences.fromJson(Map<String, dynamic> json) =>
      ReminderPreferences(
        remind24HoursBefore: json['remind24HoursBefore'] as bool,
        remind1HourBefore: json['remind1HourBefore'] as bool,
        remindAtEventTime: json['remindAtEventTime'] as bool,
        updatedAt: DateTime.parse(json['updatedAt'] as String).toUtc(),
      );

  Map<String, dynamic> toJson() => {
    'remind24HoursBefore': remind24HoursBefore,
    'remind1HourBefore': remind1HourBefore,
    'remindAtEventTime': remindAtEventTime,
  };
}
