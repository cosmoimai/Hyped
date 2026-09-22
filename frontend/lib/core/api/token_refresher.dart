import 'package:hyped/core/auth/auth_tokens.dart';

abstract interface class TokenRefresher {
  Future<AuthTokens> refresh(String refreshToken, String installationId);
}
