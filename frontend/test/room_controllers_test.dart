import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/data/room_repository.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_commands.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_controllers.dart';
import 'package:mocktail/mocktail.dart';

class MockRoomRepository extends Mock implements RoomRepository {}

void main() {
  late MockRoomRepository repository;

  setUpAll(() {
    registerFallbackValue(draft());
    registerFallbackValue(room());
  });

  setUp(() {
    repository = MockRoomRepository();
  });

  test('list controller orders nearest room first', () async {
    when(() => repository.listRooms()).thenAnswer(
      (_) async => [
        room(id: 'later', eventAt: DateTime.utc(2030, 1, 2)),
        room(id: 'soon', eventAt: DateTime.utc(2030, 1, 1)),
      ],
    );
    final controller = RoomListController(repository);

    await controller.load();

    expect(controller.nearestRoom?.id, 'soon');
    expect(controller.otherRooms.single.id, 'later');
    expect(controller.error, isNull);
  });

  test('detail controller saves through repository', () async {
    when(
      () => repository.getRoom('room-id'),
    ).thenAnswer((_) async => room(id: 'room-id'));
    when(
      () => repository.updateRoom(any(), any()),
    ).thenAnswer((_) async => room(id: 'room-id', title: 'Updated'));
    final controller = RoomDetailController(repository, 'room-id');

    await controller.load();
    final saved = await controller.save(draft(title: 'Updated'));

    expect(saved, isTrue);
    expect(controller.room?.title, 'Updated');
  });

  test('creation controller validates before calling API', () async {
    final controller = RoomCreationController(repository);

    final created = await controller.submit(draft(title: ''));

    expect(created, isNull);
    expect(controller.error, 'Add a title.');
    verifyNever(() => repository.createRoom(any()));
  });

  test(
    'creation controller maps backend validation codes to clean messages',
    () async {
      when(() => repository.createRoom(any())).thenThrow(
        const RoomApiException(code: 'EVENT_TIME_NOT_FUTURE', statusCode: 422),
      );
      final controller = RoomCreationController(repository);

      final created = await controller.submit(draft());

      expect(created, isNull);
      expect(controller.error, 'Choose a future event time.');
    },
  );

  test('creation controller returns created room', () async {
    when(() => repository.createRoom(any())).thenAnswer((_) async => room());
    final controller = RoomCreationController(repository);

    final created = await controller.submit(draft());

    expect(created?.id, 'room-id');
    expect(controller.error, isNull);
  });
}

Room room({
  String id = 'room-id',
  String title = 'Goa trip',
  DateTime? eventAt,
}) => Room(
  id: id,
  title: title,
  eventAt: eventAt ?? DateTime.utc(2030, 12, 20, 10),
  eventTimeZone: 'UTC',
  location: 'Goa',
  description: 'Trip',
  theme: const RoomTheme(
    kind: RoomThemeKind.preset,
    presetKey: 'soft-blue-01',
    overlayKey: 'dark-soft',
  ),
  status: RoomStatus.active,
  role: RoomRole.owner,
  memberCount: 2,
  revision: 1,
  createdAt: DateTime.utc(2026),
  updatedAt: DateTime.utc(2026),
  serverNow: DateTime.utc(2026),
  etag: '"room-1"',
);

RoomDraft draft({String title = 'Goa trip'}) => RoomDraft(
  title: title,
  eventDate: DateTime(2030, 12, 20),
  eventTime: (hour: 10, minute: 0),
  eventTimeZone: 'UTC',
  theme: const RoomTheme(
    kind: RoomThemeKind.preset,
    presetKey: 'soft-blue-01',
    overlayKey: 'dark-soft',
  ),
);
