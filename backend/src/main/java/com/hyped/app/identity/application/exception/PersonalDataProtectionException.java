package com.hyped.app.identity.application.exception;

import java.util.Objects;

public final class PersonalDataProtectionException extends RuntimeException {
    public enum Reason {
        INVALID_INPUT,
        MALFORMED_ENVELOPE,
        UNSUPPORTED_VERSION,
        AUTHENTICATION_FAILED,
        KEY_UNAVAILABLE
    }

    private final Reason reason;

    public PersonalDataProtectionException(Reason reason) {
        super(Objects.requireNonNull(reason, "reason").name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
