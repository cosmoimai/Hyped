import 'package:flutter/foundation.dart';

class ApiEnvironment {
  const ApiEnvironment._();
  static const _developmentUrl = 'http://10.0.2.2:8080/api/v1';
  static const _configuredUrl = String.fromEnvironment(
    'HYPED_API_BASE_URL',
    defaultValue: _developmentUrl,
  );

  static String get baseUrl {
    final uri = Uri.parse(_configuredUrl);
    if (kReleaseMode &&
        (_configuredUrl == _developmentUrl || uri.scheme != 'https')) {
      throw StateError(
        'A secure production API URL is required for release builds',
      );
    }
    return _configuredUrl;
  }
}
