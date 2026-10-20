import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/notifications/data/notification_platform.dart';
import 'package:hyped/features/notifications/data/push_api.dart';

class PushController extends ChangeNotifier {
  PushController(this.platform, this.tokens, this.api, this.appVersion);
  final NotificationPlatform platform;
  final TokenStore tokens;
  final PushApi api;
  final Future<String> Function() appVersion;
  PushStatus status = const PushStatus(
    supported: false,
    configured: false,
    enabled: false,
  );
  String? message;
  String? _pendingSession;
  String? _pendingRoom;
  bool _authenticated = false;
  bool _disposed = false;
  bool _busy = false;
  bool _again = false;
  bool get busy => _busy;
  String? _registration;

  Future<void> initialize() async {
    try {
      await platform.listen(() {
        unawaited(sync(force: true));
      }, _tap);
      status = await platform.status();
    } catch (_) {
      message = 'Notifications are unavailable for this build.';
    }
    _notify();
  }

  void setAuthenticated(bool authenticated) {
    _authenticated = authenticated;
    if (authenticated) {
      unawaited(sync());
    } else {
      _pendingRoom = null;
      _pendingSession = null;
      _registration = null;
    }
  }

  bool _current(int epoch) =>
      !_disposed && _authenticated && epoch == platform.epoch;

  Future<void> sync({bool force = false}) async {
    if (_busy) {
      _again = true;
      return;
    }
    if (!_authenticated || _disposed) return;
    final epoch = platform.epoch;
    _busy = true;
    message = null;
    _notify();
    try {
      status = await platform.status();
      if (!_current(epoch) || !status.supported || !status.configured) return;
      final session = await tokens.read();
      if (session == null || !_current(epoch)) return;
      final installation = await tokens.installationId();
      if (!_current(epoch)) return;
      if (!status.enabled) {
        await api.remove(installation, session.sessionId);
        if (!_current(epoch)) return;
        await platform.clearSession();
        final clearedEpoch = platform.epoch;
        await platform.deleteToken();
        if (!_authenticated || _disposed || clearedEpoch != platform.epoch) {
          return;
        }
        // Keep future opt-in possible for this same authenticated session.
        await platform.startSession();
        _registration = null;
        return;
      }
      if (!await platform.activate(session.sessionId, epoch) ||
          !_current(epoch)) {
        return;
      }
      final token = await platform.token();
      if (token == null || !_current(epoch)) return;
      final key = '${session.sessionId}:$token';
      if (!force && key == _registration) return;
      final version = await appVersion();
      if (!_current(epoch)) return;
      await api.register(installation, session.sessionId, token, version);
      if (_current(epoch)) _registration = key;
    } catch (_) {
      if (_current(epoch)) {
        message = 'Could not update notifications. Open settings to retry.';
      }
    } finally {
      _busy = false;
      _notify();
      if (_again && !_disposed) {
        _again = false;
        unawaited(sync(force: true));
      }
    }
  }

  Future<void> requestPermission() async {
    if (!_authenticated || _busy) return;
    final epoch = platform.epoch;
    try {
      final allowed = await platform.requestPermission();
      if (!_current(epoch)) return;
      await sync(force: true);
      if (!allowed) {
        message =
            'Notifications are off. You can still use all rooms and enable reminders in Android settings.';
      }
    } catch (_) {
      message = 'Could not enable notifications. Please retry.';
    }
    _notify();
  }

  void _tap(Object? value) {
    if (value is! Map) return;
    final session = value['sessionId'];
    final room = value['roomId'];
    final uuid = RegExp(
      r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
    );
    if (session is String &&
        room is String &&
        uuid.hasMatch(session) &&
        uuid.hasMatch(room)) {
      _pendingSession = session;
      _pendingRoom = room;
      _notify();
    }
  }

  Future<String?> takePendingLocation() async {
    if (!_authenticated || _pendingRoom == null) return null;
    final epoch = platform.epoch;
    final pendingSession = _pendingSession;
    final room = _pendingRoom;
    final session = await tokens.read();
    if (!_current(epoch) ||
        _pendingSession != pendingSession ||
        _pendingRoom != room) {
      return null;
    }
    _pendingRoom = null;
    _pendingSession = null;
    if (session?.sessionId != pendingSession) return null;
    return '/rooms/$room';
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    unawaited(platform.close());
    super.dispose();
  }
}
