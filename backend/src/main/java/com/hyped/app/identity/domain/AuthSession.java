package com.hyped.app.identity.domain;

import java.time.Instant;
import java.util.Objects;

public record AuthSession(
        SessionId id,
        UserId userId,
        DeviceId deviceId,
        TokenFamilyId tokenFamilyId,
        Instant issuedAt,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt,
        String revokeReason,
        Instant createdAt,
        Instant updatedAt) {

    public AuthSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tokenFamilyId, "tokenFamilyId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(lastUsedAt, "lastUsedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        if (lastUsedAt.isBefore(issuedAt) || lastUsedAt.isAfter(expiresAt)) {
            throw new IllegalArgumentException("lastUsedAt must be within the session lifetime");
        }
        if ((revokedAt == null) != (revokeReason == null)) {
            throw new IllegalArgumentException("revokedAt and revokeReason must both be present or both absent");
        }
        if (revokedAt != null && revokedAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("revokedAt cannot be before issuedAt");
        }
        if (revokeReason != null
                && (revokeReason.isEmpty() || revokeReason.codePointCount(0, revokeReason.length()) > 32)) {
            throw new IllegalArgumentException("revokeReason must contain 1 to 32 characters");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
    }
}
