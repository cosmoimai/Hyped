import 'package:flutter_riverpod/legacy.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/notifications/data/notification_platform.dart';
import 'package:hyped/features/notifications/data/push_api.dart';
import 'package:hyped/features/notifications/presentation/push_controller.dart';
import 'package:package_info_plus/package_info_plus.dart';

final pushControllerProvider = ChangeNotifierProvider<PushController>((ref) {
  final controller = PushController(
    ref.read(notificationPlatformProvider),
    ref.read(tokenStoreProvider),
    PushApi(ref.read(authenticatedDioProvider)),
    () async => (await PackageInfo.fromPlatform()).version,
  );
  controller.initialize();
  return controller;
});
