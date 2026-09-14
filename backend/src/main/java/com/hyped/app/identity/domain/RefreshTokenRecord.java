package com.hyped.app.identity.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public record RefreshTokenRecord(
        RefreshTokenId id,
        SessionId sessionId,
        byte[] tokenDigest,
        RefreshTokenState state,
        Instant issuedAt,
        Instant consumedAt,
        Instant expiresAt,
        RefreshTokenId replacedById,
        Instant createdAt) {

    public RefreshTokenRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(tokenDigest, "tokenDigest");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (tokenDigest.length != 32) {
            throw new IllegalArgumentException("tokenDigest must contain exactly 32 bytes");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        if (consumedAt != null && (consumedAt.isBefore(issuedAt) || consumedAt.isAfter(expiresAt))) {
            throw new IllegalArgumentException("consumedAt must be within the token lifetime");
        }
        if (state == RefreshTokenState.ACTIVE && (consumedAt != null || replacedById != null)) {
            throw new IllegalArgumentException("ACTIVE tokens cannot have consumption or replacement fields");
        }
        if (state == RefreshTokenState.CONSUMED && (consumedAt == null || replacedById == null)) {
            throw new IllegalArgumentException("CONSUMED tokens require consumedAt and replacedById");
        }
        // REVOKED records retain any existing consumption history for reuse detection.
        tokenDigest = tokenDigest.clone();
    }

    @Override
    public byte[] tokenDigest() {
        return tokenDigest.clone();
    }

    @Override
    public String toString() {
        return "RefreshTokenRecord[id=" + id + ", sessionId=" + sessionId + ", state=" + state + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RefreshTokenRecord that)) {
            return false;
        }
        return id.equals(that.id) && sessionId.equals(that.sessionId) && Arrays.equals(tokenDigest, that.tokenDigest)
                && state == that.state && issuedAt.equals(that.issuedAt) && Objects.equals(consumedAt, that.consumedAt)
                && expiresAt.equals(that.expiresAt) && Objects.equals(replacedById, that.replacedById)
                && createdAt.equals(that.createdAt);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(id, sessionId, state, issuedAt, consumedAt, expiresAt, replacedById, createdAt)
                + Arrays.hashCode(tokenDigest);
    }
}
