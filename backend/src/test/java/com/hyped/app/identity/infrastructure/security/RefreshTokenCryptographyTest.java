package com.hyped.app.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyped.app.identity.application.model.GeneratedRefreshToken;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RefreshTokenCryptographyTest {

    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");
    private final SecureRandomRefreshTokenGenerator generator = new SecureRandomRefreshTokenGenerator(
            new SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(30));
    private final Sha256RefreshTokenDigester digester = new Sha256RefreshTokenDigester();

    @Test
    void refreshTokensUseThirtyTwoRandomBytesAndUnpaddedUrlSafeBase64() {
        Set<String> values = new HashSet<>();
        for (int index = 0; index < 1000; index++) {
            GeneratedRefreshToken token = generator.generate();
            assertThat(token.tokenValue()).matches("[A-Za-z0-9_-]{43}");
            assertThat(Base64.getUrlDecoder().decode(token.tokenValue())).hasSize(32);
            assertThat(values.add(token.tokenValue())).isTrue();
            assertThat(token.issuedAt()).isEqualTo(NOW);
            assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
            assertThat(token.toString()).doesNotContain(token.tokenValue());
        }
    }

    @Test
    void digestsAreDeterministicAndDifferentTokensHaveDifferentDigests() {
        String first = generator.generate().tokenValue();
        String second = generator.generate().tokenValue();

        assertThat(digester.digest(first)).hasSize(32).containsExactly(digester.digest(first));
        assertThat(digester.digest(first)).isNotEqualTo(digester.digest(second));
        // Published SHA-256 known-answer input verifies hashing the encoded token's UTF-8 bytes.
        assertThat(HexFormat.of().formatHex(digester.digest("abc")))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void returnedDigestArraysDoNotShareMutableState() {
        String token = generator.generate().tokenValue();
        byte[] first = digester.digest(token);
        byte[] expected = first.clone();
        byte[] second = digester.digest(token);
        first[0]++;

        assertThat(second).isNotSameAs(first).containsExactly(expected);
        second[1]++;
        assertThat(digester.digest(token)).containsExactly(expected);
    }
}
