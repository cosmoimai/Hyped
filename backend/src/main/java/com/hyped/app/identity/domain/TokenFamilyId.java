package com.hyped.app.identity.domain;

import java.util.Objects;
import java.util.UUID;

public record TokenFamilyId(UUID value) {

    public TokenFamilyId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
