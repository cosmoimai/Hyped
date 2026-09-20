package com.hyped.app.identity.infrastructure.security;

import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class AccessTokenValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token", "The access token is invalid.", null);
    private final TokenProperties properties;

    public AccessTokenValidator(TokenProperties properties) {
        this.properties = properties;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (!properties.keyId().equals(token.getHeaders().get("kid"))
                || token.getAudience().size() != 1
                || !properties.audience().equals(token.getAudience().getFirst())
                || token.getIssuedAt() == null
                || token.getExpiresAt() == null
                || token.getId() == null
                || !validUuid(token.getSubject())
                || !validUuid(token.getClaimAsString("sid"))
                || !validUuid(token.getClaimAsString("did"))) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static boolean validUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
