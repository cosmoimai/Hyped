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

class AuthUser {
  const AuthUser({
    required this.id,
    required this.displayName,
    required this.photo,
    required this.profileRevision,
  });
  final String id;
  final String displayName;
  final AuthPhoto? photo;
  final int profileRevision;
  factory AuthUser.fromJson(Map<String, dynamic> json) => AuthUser(
    id: json['id'] as String,
    displayName: json['displayName'] as String,
    photo: json['photo'] == null
        ? null
        : AuthPhoto.fromJson(json['photo'] as Map<String, dynamic>),
    profileRevision: json['profileRevision'] as int,
  );
}

class AuthPhoto {
  const AuthPhoto({this.mediaAssetId, this.url});
  final String? mediaAssetId;
  final String? url;
  factory AuthPhoto.fromJson(Map<String, dynamic> json) => AuthPhoto(
    mediaAssetId: json['mediaAssetId'] as String?,
    url: json['url'] as String?,
  );
}

class AuthExchangeResponse {
  const AuthExchangeResponse({
    required this.tokens,
    required this.user,
    required this.isNewAccount,
  });
  final AuthTokens tokens;
  final AuthUser user;
  final bool isNewAccount;
  factory AuthExchangeResponse.fromJson(Map<String, dynamic> json) =>
      AuthExchangeResponse(
        tokens: AuthTokens.fromJson(json),
        user: AuthUser.fromJson(json['user'] as Map<String, dynamic>),
        isNewAccount: json['isNewAccount'] as bool,
      );

  @override
  String toString() => 'AuthExchangeResponse([REDACTED])';
}
