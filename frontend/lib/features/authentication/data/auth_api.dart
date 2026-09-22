import 'dart:io';
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
          'platform': Platform.isIOS ? 'IOS' : 'ANDROID',
          'deviceName': deviceName,
          'appVersion': appVersion,
        },
      );
      return AuthExchangeResponse.fromJson(response.data!);
    } on DioException {
      throw const AuthenticationApiException();
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
}

class AuthenticationApiException implements Exception {
  const AuthenticationApiException();
  @override
  String toString() => 'Authentication request failed';
}
