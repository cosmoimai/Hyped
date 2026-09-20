package com.hyped.app.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record UserAccount(
        UserId id,
        AccountStatus status,
        Instant lockedUntil,
        int failedAuthCount,
        String piiKeyReference,
        Instant deletionRequestedAt,
        Instant piiDestroyedAt,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt) {

    public UserAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (failedAuthCount < 0) {
            throw new IllegalArgumentException("failedAuthCount cannot be negative");
        }
        if ((status == AccountStatus.LOCKED) != (lockedUntil != null)) {
            throw new IllegalArgumentException("Only LOCKED accounts must have lockedUntil");
        }
        if (status == AccountStatus.DELETED) {
            if (deletedAt == null || piiDestroyedAt == null) {
                throw new IllegalArgumentException("DELETED accounts require deletedAt and piiDestroyedAt");
            }
        } else if (deletedAt != null || piiDestroyedAt != null) {
            throw new IllegalArgumentException("Only DELETED accounts can have deletedAt or piiDestroyedAt");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
    }

    public boolean canStartSession(Instant now) {
        Objects.requireNonNull(now, "now");
        return status == AccountStatus.ACTIVE
                || (status == AccountStatus.LOCKED && !now.isBefore(lockedUntil));
    }

    public UserAccount recordSuccessfulAuthentication(Instant now) {
        requireAuthenticationAllowed(now);
        return withAuthenticationState(AccountStatus.ACTIVE, null, 0, now);
    }

    public UserAccount recordAttributableFailure(
            Instant now, int configuredThreshold, Duration configuredLockDuration) {
        Objects.requireNonNull(configuredLockDuration, "configuredLockDuration");
        if (configuredThreshold <= 0) {
            throw new IllegalArgumentException("configuredThreshold must be positive");
        }
        if (configuredLockDuration.isZero() || configuredLockDuration.isNegative()) {
            throw new IllegalArgumentException("configuredLockDuration must be positive");
        }
        requireAuthenticationAllowed(now);
        // Only successful authentication resets the attributable failure count.
        int failures = Math.incrementExact(failedAuthCount);
        if (failures >= configuredThreshold) {
            return withAuthenticationState(
                    AccountStatus.LOCKED, now.plus(configuredLockDuration), failures, now);
        }
        return withAuthenticationState(AccountStatus.ACTIVE, null, failures, now);
    }

    private void requireAuthenticationAllowed(Instant now) {
        if (!canStartSession(now)) {
            throw new IllegalStateException("Account is not eligible for authentication");
        }
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Authentication time cannot be before updatedAt");
        }
    }

    private UserAccount withAuthenticationState(
            AccountStatus newStatus, Instant newLockedUntil, int failures, Instant now) {
        return new UserAccount(id, newStatus, newLockedUntil, failures, piiKeyReference,
                deletionRequestedAt, piiDestroyedAt, deletedAt, createdAt, now);
    }
}
