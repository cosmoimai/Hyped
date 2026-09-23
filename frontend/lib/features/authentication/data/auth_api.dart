import 'package:dio/dio.dart';
import 'package:hyped/core/api/token_refresher.dart';
import 'package:hyped/core/auth/auth_tokens.dart';

class AuthApi implements TokenRefresher {
  AuthApi(this._publicDio, this._authenticatedDio);
  final Dio _publicDio;
  final Dio _authenticatedDio;
  Future<AuthExchangeResponse> exchange({
    required String firebaseIdToken,
    required String installationId,
    required String deviceName,
    required String appVersion,
  }) async {
    try {
      final response = await _publicDio.post<Map<String, dynamic>>(
        '/auth/exchange',
        data: {
          'firebaseIdToken': firebaseIdToken,
          'installationId': installationId,
          'platform': 'ANDROID',
          'deviceName': deviceName,
          'appVersion': appVersion,
        },
      );
      return AuthExchangeResponse.fromJson(response.data!);
    } on DioException catch (error) {
      throw _mapException(error);
    }
  }

  @override
  Future<AuthTokens> refresh(String refreshToken, String installationId) async {
    try {
      final response = await _publicDio.post<Map<String, dynamic>>(
        '/auth/refresh',
        data: {'refreshToken': refreshToken, 'installationId': installationId},
      );
      return AuthTokens.fromJson(response.data!);
    } on DioException {
      throw const AuthenticationApiException();
    }
  }

  Future<void> logout() async {
    try {
      await _authenticatedDio.post<void>('/auth/logout');
    } on DioException {
      throw const AuthenticationApiException();
    }
  }

  Exception _mapException(DioException error) {
    final data = error.response?.data;
    if (data is Map<String, dynamic> &&
        data['code'] == 'DEVICE_LIMIT_REACHED') {
      final devices = data['devices'];
      if (devices is List) {
        return DeviceLimitReachedException(
          devices
              .whereType<Map>()
              .map(
                (item) =>
                    RecoveryDevice.fromJson(Map<String, dynamic>.from(item)),
              )
              .toList(growable: false),
        );
      }
    }
    return const AuthenticationApiException();
  }
}

class AuthenticationApiException implements Exception {
  const AuthenticationApiException();
  @override
  String toString() => 'Authentication request failed';
}

class DeviceLimitReachedException implements Exception {
  const DeviceLimitReachedException(this.devices);

  final List<RecoveryDevice> devices;

  @override
  String toString() => 'The active-device limit was reached';
}

class RecoveryDevice {
  const RecoveryDevice({
    required this.deviceId,
    required this.deviceName,
    required this.platform,
    required this.lastActiveAt,
  });

  final String deviceId;
  final String deviceName;
  final String platform;
  final DateTime lastActiveAt;

  factory RecoveryDevice.fromJson(Map<String, dynamic> json) => RecoveryDevice(
    deviceId: json['deviceId'] as String,
    deviceName: json['deviceName'] as String,
    platform: json['platform'] as String,
    lastActiveAt: DateTime.parse(json['lastActiveAt'] as String),
  );
}
