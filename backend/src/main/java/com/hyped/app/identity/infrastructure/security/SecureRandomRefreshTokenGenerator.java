package com.hyped.app.identity.infrastructure.security;

import com.hyped.app.identity.application.model.GeneratedRefreshToken;
import com.hyped.app.identity.application.port.out.RefreshTokenGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

final class SecureRandomRefreshTokenGenerator implements RefreshTokenGenerator {

    private final SecureRandom random;
    private final Clock clock;
    private final Duration lifetime;

    SecureRandomRefreshTokenGenerator(SecureRandom random, Clock clock, Duration lifetime) {
        this.random = Objects.requireNonNull(random, "random");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        if (lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("Refresh-token lifetime must be positive");
        }
    }

    @Override
    public GeneratedRefreshToken generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value;
        try {
            value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
        Instant issuedAt = clock.instant();
        return new GeneratedRefreshToken(value, issuedAt, issuedAt.plus(lifetime));
    }
}
