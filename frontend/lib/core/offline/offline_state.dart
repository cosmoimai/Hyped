import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

const offlineReadOnlyMessage =
    'You are offline. Rooms are read-only. Refresh when connected before making changes.';

class OfflineState extends ChangeNotifier {
  bool _offline = false;
  bool get offline => _offline;
  void setOffline(bool value) {
    if (_offline != value) {
      _offline = value;
      notifyListeners();
    }
  }
}

bool isTransportFailure(DioException error) =>
    error.response == null &&
    {
      DioExceptionType.connectionError,
      DioExceptionType.connectionTimeout,
      DioExceptionType.sendTimeout,
      DioExceptionType.receiveTimeout,
    }.contains(error.type);

class OfflineWriteInterceptor extends Interceptor {
  OfflineWriteInterceptor(this.state);
  final OfflineState state;
  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    if (state.offline &&
        !{'GET', 'HEAD', 'OPTIONS'}.contains(options.method) &&
        !options.path.endsWith('/auth/logout')) {
      handler.reject(
        DioException(
          requestOptions: options,
          type: DioExceptionType.connectionError,
          message: offlineReadOnlyMessage,
          response: Response(
            requestOptions: options,
            statusCode: 503,
            data: {'code': 'OFFLINE_READ_ONLY'},
          ),
        ),
      );
    } else {
      handler.next(options);
    }
  }

  @override
  void onResponse(Response response, ResponseInterceptorHandler handler) {
    state.setOffline(false);
    handler.next(response);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    if (isTransportFailure(err)) {
      state.setOffline(true);
    } else if (err.response != null &&
        !(err.response?.data is Map &&
            (err.response!.data as Map)['code'] == 'OFFLINE_READ_ONLY')) {
      state.setOffline(false);
    }
    handler.next(err);
  }
}
