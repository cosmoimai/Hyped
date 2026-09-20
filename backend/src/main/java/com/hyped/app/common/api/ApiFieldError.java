package com.hyped.app.common.api;

import java.util.Objects;

public record ApiFieldError(String field, String code, String message) {
    public ApiFieldError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
