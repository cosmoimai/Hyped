package com.hyped.app.room.domain;

import java.util.Objects;
import java.util.UUID;

public record RoomId(UUID value) {
    public RoomId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
