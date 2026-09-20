package com.hyped.app.identity.infrastructure.firebase;

import com.hyped.app.identity.application.exception.IdentityTokenVerificationException;
import com.hyped.app.identity.application.exception.IdentityTokenVerificationException.Reason;
import com.hyped.app.identity.application.model.VerifiedProviderIdentity;
import com.hyped.app.identity.domain.IdentityProvider;
import java.math.BigInteger;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class FirebaseTokenClaimsMapper {

    private final Clock clock;
    private final FirebaseIdentityProperties properties;

    FirebaseTokenClaimsMapper(Clock clock, FirebaseIdentityProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    VerifiedProviderIdentity map(Map<String, Object> claims) {
        try {
            if (claims == null || !(claims.get("firebase") instanceof Map<?, ?> firebase)
                    || !(firebase.get("sign_in_provider") instanceof String signInProvider)) {
                throw invalidClaims();
            }
            IdentityProvider provider = switch (signInProvider) {
                case "google.com" -> IdentityProvider.GOOGLE;
                case "apple.com" -> IdentityProvider.APPLE;
                default -> throw new IdentityTokenVerificationException(Reason.UNSUPPORTED_PROVIDER);
            };
            if (!(firebase.get("identities") instanceof Map<?, ?> identities)
                    || !(identities.get(signInProvider) instanceof List<?> subjects)
                    || subjects.size() != 1 || !(subjects.getFirst() instanceof String subject) || subject.isBlank()) {
                throw invalidClaims();
            }
            Object verifiedClaim = claims.get("email_verified");
            if (claims.containsKey("email_verified") && !(verifiedClaim instanceof Boolean)) {
                throw invalidClaims();
            }
            Instant issuedAt = epochSeconds(claims.get("iat"));
            Instant authenticatedAt = epochSeconds(claims.get("auth_time"));
            Instant latestAllowed = clock.instant().plus(properties.clockSkew());
            if (issuedAt.isAfter(latestAllowed) || authenticatedAt.isAfter(latestAllowed)
                    || authenticatedAt.isAfter(issuedAt)) {
                throw invalidClaims();
            }
            return new VerifiedProviderIdentity(provider, subject, optionalString(claims, "email"),
                    Boolean.TRUE.equals(verifiedClaim), optionalString(claims, "name"),
                    optionalString(claims, "picture"), issuedAt, authenticatedAt);
        } catch (IllegalArgumentException | DateTimeException | ArithmeticException exception) {
            throw new IdentityTokenVerificationException(Reason.INVALID_CLAIMS, exception);
        }
    }

    private static String optionalString(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string)) {
            throw invalidClaims();
        }
        return string;
    }

    private static Instant epochSeconds(Object value) {
        long seconds;
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            seconds = ((Number) value).longValue();
        } else if (value instanceof BigInteger integer) {
            seconds = integer.longValueExact();
        } else {
            throw invalidClaims();
        }
        if (seconds < 0) {
            throw invalidClaims();
        }
        return Instant.ofEpochSecond(seconds);
    }

    private static IdentityTokenVerificationException invalidClaims() {
        return new IdentityTokenVerificationException(Reason.INVALID_CLAIMS);
    }
}
