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
  });
  final Dio _dio;
  final TokenStore _tokens;
  final TokenRefresher _refresher;
  final void Function()? onAuthenticationFailed;
  Future<AuthTokens>? _refreshing;

  @override
  void onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    if (!_isPublicAuth(options.path)) {
      final tokens = await _tokens.read();
      if (tokens != null) {
        options.headers['Authorization'] = 'Bearer ${tokens.accessToken}';
      }
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    final request = err.requestOptions;
    if (err.response?.statusCode != 401 ||
        request.extra['authRetried'] == true ||
        _isPublicAuth(request.path)) {
      handler.next(err);
      return;
    }
    try {
      final current = await _tokens.read();
      final requestToken = request.headers['Authorization'];
      if (current != null && requestToken != 'Bearer ${current.accessToken}') {
        request.extra['authRetried'] = true;
        request.headers['Authorization'] = 'Bearer ${current.accessToken}';
        handler.resolve(await _dio.fetch<dynamic>(request));
        return;
      }
      final refreshed = await _singleFlightRefresh();
      request.extra['authRetried'] = true;
      request.headers['Authorization'] = 'Bearer ${refreshed.accessToken}';
      handler.resolve(await _dio.fetch<dynamic>(request));
    } catch (_) {
      await _tokens.clear();
      onAuthenticationFailed?.call();
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
    await _tokens.write(refreshed);
    return refreshed;
  }

  bool _isPublicAuth(String path) =>
      path.endsWith('/auth/exchange') || path.endsWith('/auth/refresh');
}
