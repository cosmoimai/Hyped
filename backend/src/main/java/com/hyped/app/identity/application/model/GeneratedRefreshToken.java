package com.hyped.app.identity.application.model;

import java.time.Instant;
import java.util.Objects;

public record GeneratedRefreshToken(String tokenValue, Instant issuedAt, Instant expiresAt) {

    public GeneratedRefreshToken {
        Objects.requireNonNull(tokenValue, "tokenValue");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (tokenValue.isBlank() || !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("A token requires a value and an expiry after issuance");
        }
    }

    @Override
    public String toString() {
        return "GeneratedRefreshToken[tokenValue=[REDACTED], issuedAt=" + issuedAt + ", expiresAt=" + expiresAt + "]";
    }
}
