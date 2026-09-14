package com.hyped.app.identity.domain;

import java.util.Objects;
import java.util.UUID;

public record IdentityId(UUID value) {

    public IdentityId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
