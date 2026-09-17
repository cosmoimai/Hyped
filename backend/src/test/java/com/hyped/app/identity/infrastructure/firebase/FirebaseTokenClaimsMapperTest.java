package com.hyped.app.identity.infrastructure.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.application.exception.IdentityTokenVerificationException;
import com.hyped.app.identity.application.exception.IdentityTokenVerificationException.Reason;
import com.hyped.app.identity.domain.IdentityProvider;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class FirebaseTokenClaimsMapperTest {
    static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private final FirebaseTokenClaimsMapper mapper = new FirebaseTokenClaimsMapper(CLOCK,
            new FirebaseIdentityProperties(true, "test-project", Duration.ofSeconds(60)));

    static Map<String, Object> claims(String provider) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("firebase", Map.of("sign_in_provider", provider,
                "identities", Map.of(provider, List.of(" provider-subject "), "email", List.of("other@example.com"))));
        claims.put("sub", "firebase-uid");
        claims.put("email", " person@example.com ");
        claims.put("email_verified", true);
        claims.put("name", " Personal Name ");
        claims.put("picture", "https://example.com/personal-photo");
        claims.put("iat", NOW.minusSeconds(5).getEpochSecond());
        claims.put("auth_time", NOW.minusSeconds(10).getEpochSecond());
        return claims;
    }

    @ParameterizedTest
    @CsvSource({"google.com, GOOGLE", "apple.com, APPLE"})
    void mapsSelectedProviderSubject(String provider, IdentityProvider expected) {
        var identity = mapper.map(claims(provider));
        assertThat(identity.provider()).isEqualTo(expected);
        assertThat(identity.providerSubject()).isEqualTo("provider-subject");
        assertThat(identity.email()).isEqualTo("person@example.com");
        assertThat(identity.emailVerified()).isTrue();
        assertThat(identity.displayName()).isEqualTo("Personal Name");
        assertThat(identity.tokenIssuedAt()).isEqualTo(NOW.minusSeconds(5));
        assertThat(identity.authenticatedAt()).isEqualTo(NOW.minusSeconds(10));
        assertThat(identity.toString()).doesNotContain("provider-subject", "person@example.com", "Personal Name",
                "https://example.com/personal-photo", "firebase-uid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "phone", "anonymous", "custom", "unknown"})
    void rejectsUnsupportedProviders(String provider) {
        assertFailure(claims(provider), Reason.UNSUPPORTED_PROVIDER);
    }

    static Stream<Object> malformedFirebaseClaims() {
        return Stream.of("wrong", Map.of(), Map.of("sign_in_provider", 42),
                Map.of("sign_in_provider", "google.com"),
                Map.of("sign_in_provider", "google.com", "identities", "wrong"),
                firebaseWithSubjects(List.of()), firebaseWithSubjects(List.of("one", "two")),
                firebaseWithSubjects(List.of(" ")), firebaseWithSubjects(List.of(42)),
                firebaseWithSubjects("not-a-list"), firebaseWithSubjects(List.of("s".repeat(256))));
    }

    private static Map<String, Object> firebaseWithSubjects(Object subjects) {
        return Map.of("sign_in_provider", "google.com", "identities", Map.of("google.com", subjects));
    }

    @ParameterizedTest
    @MethodSource("malformedFirebaseClaims")
    void rejectsMalformedProviderClaims(Object firebase) {
        var claims = claims("google.com");
        claims.put("firebase", firebase);
        assertFailure(claims, Reason.INVALID_CLAIMS);
    }

    @Test
    void rejectsMissingFirebaseClaimsAndSelectedIdentity() {
        var claims = claims("google.com");
        claims.remove("firebase");
        assertFailure(claims, Reason.INVALID_CLAIMS);
        claims.put("firebase", Map.of("sign_in_provider", "google.com",
                "identities", Map.of("apple.com", List.of("apple-subject"))));
        assertFailure(claims, Reason.INVALID_CLAIMS);
    }

    static Stream<Arguments> badTimes() {
        return Stream.of("iat", "auth_time").flatMap(key -> Stream.of(null, "123", 1.5, -1L,
                Long.MAX_VALUE, BigInteger.TEN.pow(100), Map.of()).map(value -> Arguments.of(key, value)));
    }

    @ParameterizedTest
    @MethodSource("badTimes")
    void rejectsMissingOrMalformedTimes(String key, Object value) {
        var claims = claims("google.com");
        if (value == null) {
            claims.remove(key);
        } else {
            claims.put(key, value);
        }
        assertFailure(claims, Reason.INVALID_CLAIMS);
    }

    @Test
    void validatesTimeOrderAndFutureSkew() {
        var claims = claims("google.com");
        claims.put("auth_time", NOW.getEpochSecond());
        assertFailure(claims, Reason.INVALID_CLAIMS);
        claims.put("iat", NOW.plusSeconds(61).getEpochSecond());
        assertFailure(claims, Reason.INVALID_CLAIMS);
        claims.put("iat", NOW.plusSeconds(60).getEpochSecond());
        claims.put("auth_time", NOW.plusSeconds(60).getEpochSecond());
        assertThat(mapper.map(claims).tokenIssuedAt()).isEqualTo(NOW.plusSeconds(60));
        var strictMapper = new FirebaseTokenClaimsMapper(CLOCK,
                new FirebaseIdentityProperties(true, "test-project", Duration.ZERO));
        assertThatThrownBy(() -> strictMapper.map(claims)).isInstanceOf(IdentityTokenVerificationException.class);
    }

    @Test
    void mapsUnverifiedAndMissingEmailWithoutCoercingBoolean() {
        var claims = claims("google.com");
        claims.put("email_verified", false);
        assertThat(mapper.map(claims).emailVerified()).isFalse();
        assertThat(mapper.map(claims).email()).isEqualTo("person@example.com");
        claims.remove("email_verified");
        claims.remove("email");
        assertThat(mapper.map(claims).email()).isNull();
        assertThat(mapper.map(claims).emailVerified()).isFalse();
        claims.put("email_verified", "true");
        assertFailure(claims, Reason.INVALID_CLAIMS);
        claims.put("email_verified", null);
        assertFailure(claims, Reason.INVALID_CLAIMS);
        claims.put("email_verified", true);
        assertFailure(claims, Reason.INVALID_CLAIMS);
    }

    @ParameterizedTest
    @CsvSource({"email,321", "name,101", "picture,2049"})
    void rejectsUnboundedOrMalformedPersonalClaims(String key, int length) {
        var claims = claims("google.com");
        claims.put(key, "x".repeat(length));
        assertFailure(claims, Reason.INVALID_CLAIMS);
        claims.put(key, 42);
        assertFailure(claims, Reason.INVALID_CLAIMS);
    }

    private void assertFailure(Map<String, Object> claims, Reason reason) {
        assertThatThrownBy(() -> mapper.map(claims)).isInstanceOfSatisfying(
                IdentityTokenVerificationException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(reason);
                    assertThat(exception.getMessage()).isEqualTo(reason.name());
                    assertThat(exception.toString()).doesNotContain("provider-subject", "person@example.com");
                });
    }
}
