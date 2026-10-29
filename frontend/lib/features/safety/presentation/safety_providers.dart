import 'package:hyped/features/safety/data/safety_invalidations.dart';
import 'package:hyped/features/rooms/data/cache/cache_providers.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/room_widget_providers.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:flutter/foundation.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/core/offline/offline_state.dart';
import 'package:hyped/features/rooms/data/room_api.dart';
import 'package:hyped/features/safety/data/safety_api.dart';
import 'package:uuid/uuid.dart';

final safetyApiProvider = Provider<SafetyApi>(
  (ref) => SafetyApi(ref.read(authenticatedDioProvider)),
);
final blockedAccountsProvider =
    FutureProvider.autoDispose<List<BlockedAccount>>((ref) async {
      ref.watch(authControllerProvider.select((auth) => auth.state.status));
      final store = ref.read(tokenStoreProvider);
      final session = (await store.read())?.sessionId;
      if (session == null) {
        throw const RoomApiException(code: 'SESSION_EXPIRED', statusCode: 401);
      }
      final accounts = await ref.read(safetyApiProvider).blocks(session);
      if ((await store.read())?.sessionId != session) {
        throw const RoomApiException(code: 'SESSION_CHANGED');
      }
      return accounts;
    });
final safetyControllerProvider =
    ChangeNotifierProvider.autoDispose<SafetyController>((ref) {
      ref.watch(authControllerProvider.select((auth) => auth.state.status));
      return SafetyController(
        ref.read(safetyApiProvider),
        ref.read(tokenStoreProvider),
        afterChange: (session) => clearSafetyProjections(
          ref.read(roomCacheSessionProvider),
          ref.read(roomWidgetRepositoryProvider),
          ref.read(tokenStoreProvider),
          session,
        ),
      );
    });

class SafetyController extends ChangeNotifier {
  SafetyController(this.api, this.tokens, {this.afterChange});
  final Future<void> Function(String)? afterChange;
  final SafetyApi api;
  final TokenStore tokens;
  bool busy = false;
  String? error;
  bool _disposed = false;
  String? _request;
  String? _key;
  String? _session;
  Future<bool> submitReport(
    String room,
    String subject,
    String? target,
    String reason,
    String? details,
  ) => _run(
    'report:$room:$subject:$target:$reason:$details',
    (session, key) => api.report(
      roomId: room,
      subjectType: subject,
      subjectUserId: target,
      reason: reason,
      details: details,
      session: session,
      key: key,
    ),
  );
  Future<bool> block(String target, String room) => _run(
    'block:$target:$room',
    (session, key) => api.block(target, room, session, key),
    change: true,
  );
  Future<bool> unblock(String target) =>
      _run('unblock:$target', (session, key) => api.unblock(target, session));
  Future<bool> _run(
    String request,
    Future<void> Function(String, String) action, {
    bool change = false,
  }) async {
    if (_disposed || busy) return false;
    busy = true;
    error = null;
    notifyListeners();
    try {
      final session = (await tokens.read())?.sessionId;
      if (session == null) {
        throw const RoomApiException(code: 'SESSION_EXPIRED', statusCode: 401);
      }
      if (_request != request || _session != session) {
        _request = request;
        _session = session;
        _key = const Uuid().v4();
      }
      await action(session, _key!);
      if (change && !_disposed && (await tokens.read())?.sessionId == session) {
        try {
          await afterChange?.call(session);
        } catch (_) {
          error =
              'Account updated. Refresh your rooms to finish synchronizing.';
        }
      }
      if (_disposed || (await tokens.read())?.sessionId != session) {
        return false;
      }
      _request = null;
      _key = null;
      return true;
    } catch (failure) {
      if (!_disposed) error = safetyError(failure);
      return false;
    } finally {
      busy = false;
      if (!_disposed) notifyListeners();
    }
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _request = null;
    _key = null;
    super.dispose();
  }
}

String safetyError(Object error) => error is RoomApiException
    ? switch (error.code) {
        'OFFLINE_READ_ONLY' => offlineReadOnlyMessage,
        'REPORT_LIMIT_REACHED' =>
          'You can submit up to five reports per day. Please try again tomorrow.',
        'VALIDATION_FAILED' =>
          'Check the report category and keep details within 500 characters.',
        'IDEMPOTENCY_KEY_REUSED' =>
          'This request changed. Close this screen and try again.',
        _ => switch (error.statusCode) {
          401 => 'Your session expired. Sign in again.',
          403 => 'This action is unavailable.',
          404 || 410 => 'This room or account is no longer available.',
          _ =>
            'Could not complete this action. Check your connection and retry.',
        },
      }
    : 'Could not complete this action. Check your connection and retry.';
