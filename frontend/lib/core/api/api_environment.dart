import 'package:flutter/foundation.dart';

class ApiEnvironment {
  const ApiEnvironment._();
  static const _developmentUrl = 'http://10.0.2.2:8080/api/v1';
  static const _configuredUrl = String.fromEnvironment(
    'HYPED_API_BASE_URL',
    defaultValue: kReleaseMode ? '' : _developmentUrl,
  );

  static String get baseUrl {
    validate(_configuredUrl, release: kReleaseMode);
    return _configuredUrl;
  }

  static void validate(String value, {required bool release}) {
    final uri = Uri.tryParse(value);
    final host = uri?.host.toLowerCase() ?? '';
    const reserved = [
      'localhost',
      'local',
      'internal',
      'invalid',
      'test',
      'example',
      'example.com',
      'example.org',
      'example.net',
    ];
    if (uri == null ||
        !uri.hasAuthority ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment ||
        uri.path != '/api/v1' ||
        !{'https', 'http'}.contains(uri.scheme) ||
        (release &&
            (uri.scheme != 'https' ||
                value != 'https://$host/api/v1' ||
                uri.hasPort ||
                !host.contains('.') ||
                !RegExp(r'^[a-z0-9.-]+$').hasMatch(host) ||
                RegExp(r'^[0-9.]+$').hasMatch(host) ||
                reserved.any(
                  (suffix) => host == suffix || host.endsWith('.$suffix'),
                )))) {
      throw StateError('A valid secure API configuration is required');
    }
  }
}
