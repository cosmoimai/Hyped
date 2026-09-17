package com.hyped.app.identity.application.exception;

import java.util.Objects;

public final class IdentityTokenVerificationException extends RuntimeException {

    public enum Reason {
        MISSING_TOKEN,
        INVALID_TOKEN,
        EXPIRED_TOKEN,
        REVOKED_TOKEN,
        UNSUPPORTED_PROVIDER,
        INVALID_CLAIMS,
        VERIFIER_UNAVAILABLE
    }

    private final Reason reason;

    public IdentityTokenVerificationException(Reason reason) {
        this(reason, null);
    }

    public IdentityTokenVerificationException(Reason reason, Throwable cause) {
        super(Objects.requireNonNull(reason, "reason").name(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
