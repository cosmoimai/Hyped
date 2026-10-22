import 'package:hyped/features/rooms/data/cache/cache_providers.dart';
import 'package:hyped/features/rooms/data/cache/cached_room_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/room_widget_providers.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/rooms/data/room_repository.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_controllers.dart';

final roomApiProvider = Provider<RoomApi>(
  (ref) => RoomApi(ref.read(authenticatedDioProvider)),
);

final roomRepositoryProvider = Provider<RoomRepository>(
  (ref) => CachedRoomRepository(
    ref.read(roomApiProvider),
    ref.read(roomCacheSessionProvider),
    ref.read(tokenStoreProvider),
  ),
);

final roomListControllerProvider =
    ChangeNotifierProvider.autoDispose<RoomListController>((ref) {
      ref.watch(authControllerProvider.select((auth) => auth.state.status));
      final controller = RoomListController(
        ref.read(roomRepositoryProvider),
        ref.read(roomWidgetRepositoryProvider),
      );
      controller.load();
      return controller;
    });

final roomDetailControllerProvider = ChangeNotifierProvider.autoDispose
    .family<RoomDetailController, String>((ref, roomId) {
      ref.watch(authControllerProvider.select((auth) => auth.state.status));
      final controller = RoomDetailController(
        ref.read(roomRepositoryProvider),
        roomId,
        ref.read(roomWidgetRepositoryProvider),
      );
      controller.load();
      return controller;
    });

final roomCreationControllerProvider =
    ChangeNotifierProvider<RoomCreationController>((ref) {
      ref.watch(authControllerProvider.select((auth) => auth.state.status));
      return RoomCreationController(ref.read(roomRepositoryProvider));
    });
