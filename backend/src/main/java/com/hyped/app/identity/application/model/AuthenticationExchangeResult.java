package com.hyped.app.identity.application.model;

import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public sealed interface AuthenticationExchangeResult {

    record Success(SessionTokenPair tokens, UserId userId, boolean newAccount)
            implements AuthenticationExchangeResult {
        public Success {
            Objects.requireNonNull(tokens, "tokens");
            Objects.requireNonNull(userId, "userId");
        }
    }

    record AccountUnavailable(AccountStatus status, Instant lockedUntil)
            implements AuthenticationExchangeResult {
        public AccountUnavailable {
            Objects.requireNonNull(status, "status");
            if (status == AccountStatus.ACTIVE || (status == AccountStatus.LOCKED && lockedUntil == null)) {
                throw new IllegalArgumentException("Unavailable account state is inconsistent");
            }
            if (status != AccountStatus.LOCKED && lockedUntil != null) {
                throw new IllegalArgumentException("Only locked accounts expose a lock expiry");
            }
        }
    }

    record DeviceLimitReached(List<DeviceSummary> devices) implements AuthenticationExchangeResult {
        public DeviceLimitReached {
            devices = List.copyOf(devices);
        }
    }

    record AccountLinkConfirmationRequired() implements AuthenticationExchangeResult {
    }
}
