class AuthTokens {
  const AuthTokens({
    required this.accessToken,
    required this.refreshToken,
    required this.accessTokenExpiresAt,
    required this.refreshTokenExpiresAt,
    required this.sessionId,
  });
  final String accessToken;
  final String refreshToken;
  final DateTime accessTokenExpiresAt;
  final DateTime refreshTokenExpiresAt;
  final String sessionId;

  factory AuthTokens.fromJson(Map<String, dynamic> json) => AuthTokens(
    accessToken: json['accessToken'] as String,
    refreshToken: json['refreshToken'] as String,
    accessTokenExpiresAt: DateTime.parse(
      json['accessTokenExpiresAt'] as String,
    ),
    refreshTokenExpiresAt: DateTime.parse(
      json['refreshTokenExpiresAt'] as String,
    ),
    sessionId: json['sessionId'] as String,
  );

  @override
  String toString() => 'AuthTokens([REDACTED], sessionId: $sessionId)';
}
