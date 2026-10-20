import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/room_widget_controller.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';

final roomWidgetRepositoryProvider = Provider<RoomWidgetRepository>((ref) {
  if (!kIsWeb && Platform.isAndroid) {
    return MethodChannelRoomWidgetRepository();
  }
  return const NoopRoomWidgetRepository();
});

final roomWidgetControllerProvider = ChangeNotifierProvider.autoDispose
    .family<RoomWidgetController, int?>((ref, preferredId) {
      final controller = RoomWidgetController(
        ref.read(roomWidgetRepositoryProvider),
        ref.read(roomRepositoryProvider).getRoom,
      );
      controller.load(preferredId: preferredId);
      return controller;
    });
