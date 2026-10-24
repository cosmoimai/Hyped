import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:uuid/uuid.dart';

String integrityDigest(List<int> bytes) =>
    base64Url.encode(sha256.convert(bytes).bytes).replaceAll('=', '');

String integrityBinding({
  required String method,
  required Uri uri,
  required List<int> body,
  required String authorization,
  required String id,
  required String time,
  String idempotency = '',
  String revision = '',
}) => integrityDigest(
  utf8.encode(
    [
      'hyped-integrity-v1',
      method,
      uri.path,
      uri.hasQuery ? uri.query : '',
      integrityDigest(body),
      integrityDigest(utf8.encode(authorization)),
      id,
      time,
      integrityDigest(utf8.encode(idempotency)),
      integrityDigest(utf8.encode(revision)),
    ].join('\n'),
  ),
);

bool integrityProtected(String method, String path) =>
    path.startsWith('/api/v1/') &&
    !{'GET', 'HEAD', 'OPTIONS'}.contains(method) &&
    !path.startsWith('/api/v1/auth/') &&
    !path.startsWith('/api/v1/public/') &&
    path != '/api/v1/me/deletion-requests';

class AndroidIntegrityPlatform {
  static const _channel = MethodChannel('hyped/play_integrity');
  Future<String?> token(String hash) async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return null;
    try {
      return await _channel
          .invokeMethod<String>('token', hash)
          .timeout(const Duration(seconds: 8));
    } catch (_) {
      return null; // Evidence/SDK exceptions never enter diagnostics.
    }
  }
}

/// Runs after authentication and offline checks. Retries obtain fresh evidence.
/// Missing evidence is sent to the server, whose mode alone decides enforcement.
class IntegrityInterceptor extends Interceptor {
  IntegrityInterceptor(
    this.acquire, {
    DateTime Function()? clock,
    String Function()? id,
    this.currentSession,
  }) : clock = clock ?? DateTime.now,
       id = id ?? const Uuid().v4;
  final Future<String?> Function(String) acquire;
  final Future<String?> Function()? currentSession;
  final DateTime Function() clock;
  final String Function() id;

  @override
  void onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    options.headers.remove('X-Play-Integrity');
    options.headers.remove('X-Integrity-Id');
    options.headers.remove('X-Integrity-Time');
    if (!integrityProtected(options.method, options.uri.path) ||
        options.headers['Authorization'] is! String) {
      handler.next(options);
      return;
    }
    try {
      final expected = options.extra['authenticatedSessionId'];
      if (currentSession != null) {
        options.extra.putIfAbsent('expectedSessionId', () => expected);
        if (expected is! String || await currentSession!() != expected) {
          _cancel(options, handler);
          return;
        }
      }
      final data = options.data;
      final body = data == null
          ? ''
          : data is String
          ? data
          : jsonEncode(data);
      // Freeze the exact UTF-8 JSON bytes before Dio's transformer serializes them.
      if (data != null) {
        options.data = body;
        options.contentType = Headers.jsonContentType;
      }
      final requestId = id();
      final time = clock().millisecondsSinceEpoch.toString();
      final hash = integrityBinding(
        method: options.method,
        uri: options.uri,
        body: utf8.encode(body),
        authorization: options.headers['Authorization'] as String,
        id: requestId,
        time: time,
        idempotency: options.headers['Idempotency-Key']?.toString() ?? '',
        revision: options.headers['If-Match']?.toString() ?? '',
      );
      final evidence = await acquire(hash);
      if (currentSession != null && await currentSession!() != expected) {
        _cancel(options, handler);
        return;
      }
      if (evidence != null &&
          evidence.length <= 32768 &&
          RegExp(r'^[A-Za-z0-9._-]+$').hasMatch(evidence)) {
        options.headers['X-Play-Integrity'] = evidence;
        options.headers['X-Integrity-Id'] = requestId;
        options.headers['X-Integrity-Time'] = time;
      }
      handler.next(options);
    } catch (_) {
      handler.reject(
        DioException(
          requestOptions: options,
          message: 'Could not prepare this app request. Please try again.',
        ),
      );
    }
  }

  void _cancel(RequestOptions options, RequestInterceptorHandler handler) {
    handler.reject(
      DioException(
        requestOptions: options,
        type: DioExceptionType.cancel,
        message: 'The session changed. Please try again.',
      ),
    );
  }
}
