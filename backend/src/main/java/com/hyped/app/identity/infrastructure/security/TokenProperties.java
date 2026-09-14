package com.hyped.app.identity.infrastructure.security;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

@ConfigurationProperties("hyped.tokens")
public record TokenProperties(
        @DefaultValue("false") boolean enabled,
        String issuer,
        String audience,
        String keyId,
        Resource publicKey,
        Resource privateKey,
        @DefaultValue("1h") Duration accessTokenLifetime,
        @DefaultValue("30d") Duration refreshTokenLifetime) {

    public TokenProperties {
        requirePositiveSeconds(accessTokenLifetime, "accessTokenLifetime");
        requirePositiveSeconds(refreshTokenLifetime, "refreshTokenLifetime");
        if (enabled) {
            if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                    || keyId == null || keyId.isBlank()) {
                throw new IllegalArgumentException("Token signing requires issuer, audience and keyId");
            }
            Objects.requireNonNull(publicKey, "Token signing requires publicKey");
            Objects.requireNonNull(privateKey, "Token signing requires privateKey");
        }
    }

    private static void requirePositiveSeconds(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.isZero() || duration.getNano() != 0) {
            throw new IllegalArgumentException(name + " must be a positive whole number of seconds");
        }
    }

    @Override
    public String toString() {
        return "TokenProperties[enabled=" + enabled + ", key configuration=[REDACTED]]";
    }
}
