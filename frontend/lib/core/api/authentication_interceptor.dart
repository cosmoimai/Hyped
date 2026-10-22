import 'dart:async';
import 'package:dio/dio.dart';
import 'package:hyped/core/api/token_refresher.dart';
import 'package:hyped/core/auth/auth_tokens.dart';
import 'package:hyped/core/secure_storage/token_store.dart';

class AuthenticationInterceptor extends Interceptor {
  AuthenticationInterceptor(
    this._dio,
    this._tokens,
    this._refresher, {
    this.onAuthenticationFailed,
    this.onAccountDeleted,
  });
  final Dio _dio;
  final TokenStore _tokens;
  final TokenRefresher _refresher;
  final void Function()? onAuthenticationFailed;
  final void Function()? onAccountDeleted;
  Future<AuthTokens>? _refreshing;

  @override
  void onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    if (!_isPublicAuth(options.path)) {
      final tokens = await _tokens.read();
      final expected = options.extra['expectedSessionId'];
      if (expected != null && tokens?.sessionId != expected) {
        handler.reject(
          DioException(
            requestOptions: options,
            type: DioExceptionType.cancel,
            message: 'The session changed.',
          ),
        );
        return;
      }
      if (tokens != null) {
        options.headers['Authorization'] = 'Bearer ${tokens.accessToken}';
      }
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    final request = err.requestOptions;
    final body = err.response?.data;
    final code = body is Map ? body['code'] : null;
    if (code == 'ACCOUNT_DELETED' || code == 'ACCOUNT_DELETION_PENDING') {
      try {
        onAccountDeleted?.call();
      } catch (_) {}
      await _invalidate();
      handler.next(err);
      return;
    }

    if (code == 'RECENT_AUTHENTICATION_REQUIRED') {
      handler.next(err);
      return;
    }

    if (err.response?.statusCode != 401 ||
        request.extra['authRetried'] == true ||
        _isPublicAuth(request.path)) {
      handler.next(err);
      return;
    }
    String? refreshingSession;
    try {
      final current = await _tokens.read();
      refreshingSession = current?.sessionId;
      final expected = request.extra['expectedSessionId'];
      if (expected != null && current?.sessionId != expected) {
        handler.next(err);
        return;
      }
      final requestToken = request.headers['Authorization'];
      if (current != null && requestToken != 'Bearer ${current.accessToken}') {
        request.extra['authRetried'] = true;
        request.headers['Authorization'] = 'Bearer ${current.accessToken}';
        handler.resolve(await _dio.fetch<dynamic>(request));
        return;
      }
      final refreshed = await _singleFlightRefresh();
      refreshingSession = refreshed.sessionId;
      request.extra['authRetried'] = true;
      request.headers['Authorization'] = 'Bearer ${refreshed.accessToken}';
      handler.resolve(await _dio.fetch<dynamic>(request));
    } catch (_) {
      final expected = request.extra['expectedSessionId'];
      final latest = await _tokens.read();
      if ((expected != null && latest?.sessionId != expected) ||
          (refreshingSession != null &&
              latest?.sessionId != refreshingSession)) {
        handler.next(err);
        return;
      }
      await _invalidate();
      handler.next(err);
    }
  }

  Future<AuthTokens> _singleFlightRefresh() {
    final inFlight = _refreshing;
    if (inFlight != null) return inFlight;
    final future = _performRefresh();
    _refreshing = future;
    return future.whenComplete(() {
      if (identical(_refreshing, future)) _refreshing = null;
    });
  }

  Future<AuthTokens> _performRefresh() async {
    final current = await _tokens.read();
    if (current == null) throw StateError('No refresh credential');
    final refreshed = await _refresher.refresh(
      current.refreshToken,
      await _tokens.installationId(),
    );
    final latest = await _tokens.read();
    if (latest?.sessionId != current.sessionId ||
        latest?.refreshToken != current.refreshToken) {
      throw StateError('The session changed.');
    }
    await _tokens.write(refreshed);
    return refreshed;
  }

  Future<void> _invalidate() async {
    try {
      await _tokens.clear();
    } catch (_) {
      // Local notification/SDK cleanup must not leave an interceptor request unresolved.
    } finally {
      onAuthenticationFailed?.call();
    }
  }

  bool _isPublicAuth(String path) =>
      path.endsWith('/auth/exchange') || path.endsWith('/auth/refresh');
}
