import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/presentation/screens/room_form_screen.dart';
import 'room_api_test.dart' as fixtures;

void main() {
  for (final sample in [
    (zone: 'UTC', day: 20, hour: 4, minute: 30),
    (zone: 'Asia/Kolkata', day: 20, hour: 10, minute: 0),
    (zone: 'America/Los_Angeles', day: 19, hour: 20, minute: 30),
    (zone: 'Australia/Sydney', day: 20, hour: 15, minute: 30),
  ]) {
    test(
      'edit retains event instant and room-local fields in ${sample.zone}',
      () {
        final room = Room.fromJson({
          ...fixtures.roomJson(),
          'eventTimeZone': sample.zone,
        });
        final model = RoomFormModel.fromRoom(room);
        addTearDown(model.dispose);
        expect(model.date, DateTime(2030, 12, sample.day));
        expect(model.time, (hour: sample.hour, minute: sample.minute));
        expect(model.timeZone, sample.zone);
        expect(model.previewInstant, room.eventAt);
        expect(model.draft.eventTimeZone, sample.zone);
      },
    );
  }
}
