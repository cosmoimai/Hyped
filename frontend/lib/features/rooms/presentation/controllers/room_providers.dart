import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/data/room_repository.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_controllers.dart';

final roomApiProvider = Provider<RoomApi>(
  (ref) => RoomApi(ref.read(authenticatedDioProvider)),
);

final roomRepositoryProvider = Provider<RoomRepository>(
  (ref) => RoomRepository(ref.read(roomApiProvider)),
);

final roomListControllerProvider = ChangeNotifierProvider<RoomListController>((
  ref,
) {
  final controller = RoomListController(ref.read(roomRepositoryProvider));
  controller.load();
  return controller;
});

final roomDetailControllerProvider =
    ChangeNotifierProvider.family<RoomDetailController, String>((ref, roomId) {
      final controller = RoomDetailController(
        ref.read(roomRepositoryProvider),
        roomId,
      );
      controller.load();
      return controller;
    });

final roomCreationControllerProvider =
    ChangeNotifierProvider<RoomCreationController>(
      (ref) => RoomCreationController(ref.read(roomRepositoryProvider)),
    );
