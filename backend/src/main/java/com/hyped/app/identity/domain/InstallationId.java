package com.hyped.app.identity.domain;

import java.util.Objects;
import java.util.UUID;

public record InstallationId(UUID value) {

    public InstallationId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
