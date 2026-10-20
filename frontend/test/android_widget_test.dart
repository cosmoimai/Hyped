import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/android_widgets/domain/room_widget_data.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/room_widget_controller.dart';
import 'package:hyped/features/rooms/data/room_repository.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_controllers.dart';
import 'package:mocktail/mocktail.dart';

class _MockRoomRepository extends Mock implements RoomRepository {}

void main() {
  test('widget view data formats future and completed countdown states', () {
    final now = DateTime.utc(2030, 1, 1, 10);
    final future = RoomWidgetViewData.fromData(
      RoomWidgetData.fromRoom(
        room(eventAt: now.add(const Duration(days: 2, hours: 3))),
        syncedAt: now,
      ),
      now,
    );
    final completed = RoomWidgetViewData.fromData(
      RoomWidgetData.fromRoom(
        room(eventAt: now.subtract(const Duration(minutes: 1))),
        syncedAt: now,
      ),
      now,
    );

    expect(future.title, 'Goa trip');
    expect(future.countdownText, '2d 3h');
    expect(future.completed, isFalse);
    expect(completed.countdownText, 'Event started');
    expect(completed.completed, isTrue);
  });

  test('widget data maps room fields to a platform-safe payload', () {
    final data = RoomWidgetData.fromRoom(
      room(),
      syncedAt: DateTime.utc(2030, 1, 1),
    );
    final roundTrip = RoomWidgetData.fromJson(data.toJson());

    expect(data.toJson(), {
      'roomId': 'room-id',
      'title': 'Goa trip',
      'eventAt': '2030-12-20T04:30:00.000Z',
      'eventTimeZone': 'Asia/Kolkata',
      'syncedAt': '2030-01-01T00:00:00.000Z',
    });
    expect(roundTrip.roomId, data.roomId);
    expect(roundTrip.eventAt, data.eventAt);
  });

  test('widget controller persists selected room mapping locally', () async {
    final repository = _FakeRoomWidgetRepository();
    final controller = RoomWidgetController(repository);

    await controller.load();
    expect(controller.selectedRoomId, isNull);

    await controller.select(room());
    expect(controller.selectedRoomId, 'room-id');
    expect(repository.selected, 'room-id');
    expect(repository.cached.single.roomId, 'room-id');

    await controller.clear();
    expect(controller.selectedRoomId, isNull);
    expect(repository.selected, isNull);
  });

  test(
    'room list and detail loads sync latest room data for offline widgets',
    () async {
      final roomRepository = _MockRoomRepository();
      final widgetRepository = _FakeRoomWidgetRepository();
      final first = room(id: 'first', eventAt: DateTime.utc(2030, 1, 2));
      final soon = room(id: 'soon', eventAt: DateTime.utc(2030, 1, 1));
      when(
        () => roomRepository.listRooms(),
      ).thenAnswer((_) async => [first, soon]);
      when(() => roomRepository.getRoom('soon')).thenAnswer((_) async => soon);

      final list = RoomListController(roomRepository, widgetRepository);
      await list.load();

      expect(list.nearestRoom?.id, 'soon');
      expect(widgetRepository.cached.map((data) => data.roomId), [
        'soon',
        'first',
      ]);

      final detail = RoomDetailController(
        roomRepository,
        'soon',
        widgetRepository,
      );
      await detail.load();

      expect(widgetRepository.cached.last.roomId, 'soon');
    },
  );
}

class _FakeRoomWidgetRepository implements RoomWidgetRepository {
  final cached = <RoomWidgetData>[];
  String? selected;

  @override
  Future<void> cacheRoom(Room room) async {
    cached.add(RoomWidgetData.fromRoom(room, syncedAt: DateTime.utc(2030)));
  }

  @override
  Future<void> cacheRooms(List<Room> rooms) async {
    cached
      ..clear()
      ..addAll(
        rooms.map(
          (room) => RoomWidgetData.fromRoom(room, syncedAt: DateTime.utc(2030)),
        ),
      );
  }

  @override
  Future<void> clearSelection() async {
    selected = null;
  }

  @override
  Future<void> selectRoom(String roomId) async {
    selected = roomId;
  }

  @override
  Future<String?> selectedRoomId() async => selected;
}

Room room({String id = 'room-id', DateTime? eventAt}) => Room(
  id: id,
  title: 'Goa trip',
  eventAt: eventAt ?? DateTime.utc(2030, 12, 20, 4, 30),
  eventTimeZone: 'Asia/Kolkata',
  theme: const RoomTheme(
    kind: RoomThemeKind.preset,
    presetKey: 'soft-blue-01',
    overlayKey: 'dark-soft',
  ),
  status: RoomStatus.active,
  role: RoomRole.member,
  memberCount: 8,
  revision: 4,
  createdAt: DateTime.utc(2026, 9, 9, 10),
  updatedAt: DateTime.utc(2026, 9, 9, 10, 30),
  serverNow: DateTime.utc(2026, 9, 9, 11),
);
