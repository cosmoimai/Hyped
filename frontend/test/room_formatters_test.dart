import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_formatters.dart';

void main() {
  test('countdown formats future, near-event and completed event states', () {
    final now = DateTime.utc(2030, 1, 1, 12);

    expect(countdown(now.add(const Duration(days: 2, hours: 3)), now), '2d 3h');
    expect(
      countdown(now.add(const Duration(hours: 1, minutes: 5)), now),
      '1h 5m',
    );
    expect(
      countdown(now.add(const Duration(seconds: 30)), now),
      'Less than 1m',
    );
    expect(countdown(now, now), 'Event started');
    expect(
      countdown(now.subtract(const Duration(minutes: 1)), now),
      'Event started',
    );
  });
}
