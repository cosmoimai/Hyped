import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/features/home/presentation/screens/home_screen.dart';
import 'package:hyped/features/rooms/data/room_repository.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/rooms/presentation/screens/room_detail_screen.dart';
import 'package:hyped/features/rooms/presentation/screens/room_form_screen.dart';
import 'package:mocktail/mocktail.dart';

class MockRoomRepository extends Mock implements RoomRepository {}

void main() {
  testWidgets('home shows nearest room as featured card and others compact', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          roomRepositoryProvider.overrideWith(
            (ref) => repositoryWithRooms([
              room(
                id: 'later',
                title: 'Later',
                eventAt: DateTime.utc(2030, 1, 2),
              ),
              room(
                id: 'soon',
                title: 'Soon',
                eventAt: DateTime.utc(2030, 1, 1),
              ),
            ]),
          ),
        ],
        child: const MaterialApp(home: HomeScreen()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Next up'), findsOneWidget);
    expect(find.text('Soon'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Later'), 200);
    expect(find.text('Later'), findsOneWidget);
  });

  testWidgets('direct room detail has a working back button to Home', (
    tester,
  ) async {
    final router = GoRouter(
      initialLocation: '/rooms/room-id',
      routes: [
        GoRoute(
          path: '/home',
          builder: (_, _) => const Scaffold(body: Text('Home destination')),
        ),
        GoRoute(
          path: '/rooms/:roomId',
          builder: (_, _) => const RoomDetailScreen(roomId: 'room-id'),
        ),
      ],
    );
    addTearDown(router.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          roomRepositoryProvider.overrideWith(
            (ref) => repositoryWithDetail(room()),
          ),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();
    expect(router.canPop(), isFalse);
    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    expect(find.text('Home destination'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('room detail shows countdown data and edit action for hosts', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          roomRepositoryProvider.overrideWith(
            (ref) => repositoryWithDetail(room()),
          ),
        ],
        child: const MaterialApp(home: RoomDetailScreen(roomId: 'room-id')),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Goa trip'), findsOneWidget);
    expect(find.text('2 members'), findsOneWidget);
    expect(find.byTooltip('Edit room'), findsOneWidget);
  });

  testWidgets('room detail hides edit action for members', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          roomRepositoryProvider.overrideWith(
            (ref) => repositoryWithDetail(room(role: RoomRole.member)),
          ),
        ],
        child: const MaterialApp(home: RoomDetailScreen(roomId: 'room-id')),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Goa trip'), findsOneWidget);
    expect(find.byTooltip('Edit room'), findsNothing);
  });

  testWidgets(
    'archived owner opening edit route sees archive state instead of an editable form',
    (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            roomRepositoryProvider.overrideWith(
              (ref) => repositoryWithDetail(
                room().copyWith(
                  status: RoomStatus.archived,
                  archivedAt: DateTime.utc(2026),
                  deleteAfter: DateTime.utc(2026, 1, 2),
                ),
              ),
            ),
          ],
          child: const MaterialApp(home: EditRoomScreen(roomId: 'room-id')),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Archived rooms cannot be edited.'), findsOneWidget);
      expect(find.byType(TextFormField), findsNothing);
    },
  );

  testWidgets('create room flow validates missing title', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          roomRepositoryProvider.overrideWith((ref) => repositoryWithRooms([])),
        ],
        child: const MaterialApp(home: CreateRoomScreen()),
      ),
    );

    await tester.tap(find.text('Continue'));
    await tester.pumpAndSettle();

    expect(find.text('Add a title.'), findsOneWidget);
  });
}

RoomRepository repositoryWithRooms(List<Room> rooms) {
  final repository = MockRoomRepository();
  when(() => repository.listRooms()).thenAnswer((_) async => rooms);
  return repository;
}

RoomRepository repositoryWithDetail(Room detail) {
  final repository = MockRoomRepository();
  when(() => repository.getRoom(detail.id)).thenAnswer((_) async => detail);
  return repository;
}

Room room({
  String id = 'room-id',
  String title = 'Goa trip',
  DateTime? eventAt,
  RoomRole role = RoomRole.owner,
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
  role: role,
  memberCount: 2,
  revision: 1,
  createdAt: DateTime.utc(2026),
  updatedAt: DateTime.utc(2026),
  serverNow: DateTime.utc(2026),
  etag: '"room-1"',
);
