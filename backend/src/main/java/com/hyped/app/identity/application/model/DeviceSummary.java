package com.hyped.app.identity.application.model;

import com.hyped.app.identity.domain.DeviceId;
import com.hyped.app.identity.domain.DevicePlatform;
import java.time.Instant;
import java.util.Objects;

public record DeviceSummary(DeviceId id, String name, DevicePlatform platform, Instant lastActiveAt) {

    public DeviceSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(lastActiveAt, "lastActiveAt");
    }
}
