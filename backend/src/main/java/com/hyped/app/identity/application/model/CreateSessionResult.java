package com.hyped.app.identity.application.model;

import com.hyped.app.identity.domain.AccountStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public sealed interface CreateSessionResult {

    record Success(SessionTokenPair tokens) implements CreateSessionResult {
        public Success {
            Objects.requireNonNull(tokens, "tokens");
        }
    }

    record AccountLocked(Instant lockedUntil) implements CreateSessionResult {
        public AccountLocked {
            Objects.requireNonNull(lockedUntil, "lockedUntil");
        }
    }

    record AccountBlocked(AccountStatus status) implements CreateSessionResult {
        public AccountBlocked {
            Objects.requireNonNull(status, "status");
            if (status == AccountStatus.ACTIVE || status == AccountStatus.LOCKED) {
                throw new IllegalArgumentException("Blocked result requires an unavailable account status");
            }
        }
    }

    record DeviceLimitReached(List<DeviceSummary> devices) implements CreateSessionResult {
        public DeviceLimitReached {
            devices = List.copyOf(devices);
        }
    }
}
