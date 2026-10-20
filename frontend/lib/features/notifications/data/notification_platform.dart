import 'dart:async';
import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class PushStatus {
  const PushStatus({
    required this.supported,
    required this.configured,
    required this.enabled,
  });
  final bool supported;
  final bool configured;
  final bool enabled;
}

abstract class NotificationPlatform {
  int get epoch;
  Future<void> startSession();
  Future<void> clearSession();
  Future<bool> activate(String sessionId, int epoch);
  Future<PushStatus> status();
  Future<bool> requestPermission();
  Future<void> openSettings();
  Future<String?> token();
  Future<void> deleteToken();
  Future<void> listen(
    void Function() tokenChanged,
    void Function(Object?) tapped,
  );
  Future<void> close();
}

class NoopNotificationPlatform implements NotificationPlatform {
  @override
  int get epoch => 0;
  @override
  Future<void> startSession() async {}
  @override
  Future<void> clearSession() async {}
  @override
  Future<bool> activate(String sessionId, int epoch) async => false;
  @override
  Future<PushStatus> status() async =>
      const PushStatus(supported: false, configured: false, enabled: false);
  @override
  Future<bool> requestPermission() async => false;
  @override
  Future<void> openSettings() async {}
  @override
  Future<String?> token() async => null;
  @override
  Future<void> deleteToken() async {}
  @override
  Future<void> listen(
    void Function() tokenChanged,
    void Function(Object?) tapped,
  ) async {}
  @override
  Future<void> close() async {}
}

class AndroidNotificationPlatform implements NotificationPlatform {
  AndroidNotificationPlatform([
    this.channel = const MethodChannel('hyped/notifications'),
  ]);
  final MethodChannel channel;
  int _epoch = 0;
  bool _allowed = false;
  @override
  int get epoch => _epoch;
  @override
  Future<void> startSession() async {
    _epoch++;
    _allowed = true;
  }

  @override
  Future<void> clearSession() async {
    _epoch++;
    _allowed = false;
    await channel.invokeMethod<void>('clearSession');
  }

  @override
  Future<bool> activate(String sessionId, int epoch) async {
    if (!_allowed || epoch != _epoch) return false;
    return await channel.invokeMethod<bool>('activateSession', sessionId) ??
        false;
  }

  @override
  Future<PushStatus> status() async {
    final value =
        await channel.invokeMapMethod<String, Object?>('status') ?? {};
    return PushStatus(
      supported: value['supported'] == true,
      configured: value['configured'] == true,
      enabled: value['enabled'] == true,
    );
  }

  @override
  Future<bool> requestPermission() async =>
      await channel.invokeMethod<bool>('requestPermission') ?? false;
  @override
  Future<void> openSettings() => channel.invokeMethod<void>('openSettings');
  @override
  Future<String?> token() => channel.invokeMethod<String>('getToken');
  @override
  Future<void> deleteToken() => channel.invokeMethod<void>('deleteToken');
  @override
  Future<void> listen(
    void Function() tokenChanged,
    void Function(Object?) tapped,
  ) async {
    channel.setMethodCallHandler((call) async {
      if (call.method == 'tokenChanged') tokenChanged();
      if (call.method == 'notificationTapped') tapped(call.arguments);
    });
    tapped(await channel.invokeMethod<Object?>('getInitialTap'));
  }

  @override
  Future<void> close() async {
    channel.setMethodCallHandler(null);
  }
}

final notificationPlatformProvider = Provider<NotificationPlatform>(
  (ref) => !kIsWeb && Platform.isAndroid
      ? AndroidNotificationPlatform()
      : NoopNotificationPlatform(),
);
