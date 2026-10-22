import 'package:dio/dio.dart';
import 'package:hyped/features/rooms/data/cache/room_cache_session.dart';

class CacheInvalidationInterceptor extends Interceptor {
  CacheInvalidationInterceptor(this.cache);
  final RoomCacheSession cache;
  Future<void> _remove(String id) async {
    final identity = cache.current;
    if (identity == null) return;
    cache.invalidateReads(identity);
    try {
      await cache.run(
        identity,
        () => cache.database.remove(identity.accountId, id),
      );
    } catch (_) {}
  }

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    // Capture scope at dispatch so old-account responses cannot invalidate replacement-account content.
    options.extra['roomCacheEpoch'] = cache.current?.epoch;
    handler.next(options);
  }

  bool _current(RequestOptions options) =>
      options.extra['roomCacheEpoch'] != null &&
      options.extra['roomCacheEpoch'] == cache.current?.epoch;
  @override
  void onResponse(Response response, ResponseInterceptorHandler handler) async {
    final path = response.requestOptions.path;
    final room = RegExp(
      r'^/rooms/([^/]+)(/(members/[^/]+|ownership-transfer|leave|restore|permanent|event-theme))?$',
    ).firstMatch(path);
    if (_current(response.requestOptions) &&
        room != null &&
        response.requestOptions.method != 'GET') {
      await _remove(room[1]!);
    }
    handler.next(response);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    final room = RegExp(
      r'^/rooms/([^/]+)(/.*)?$',
    ).firstMatch(err.requestOptions.path);
    if (_current(err.requestOptions) &&
        room != null &&
        ([404, 410].contains(err.response?.statusCode) ||
            (err.requestOptions.method == 'GET' &&
                err.response?.statusCode == 403))) {
      await _remove(room[1]!);
    }
    handler.next(err);
  }
}
