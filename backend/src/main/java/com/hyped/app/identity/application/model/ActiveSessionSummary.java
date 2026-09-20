package com.hyped.app.identity.application.model;

import com.hyped.app.identity.domain.DevicePlatform;
import com.hyped.app.identity.domain.SessionId;
import java.time.Instant;
import java.util.Objects;

public record ActiveSessionSummary(
        SessionId sessionId,
        String deviceName,
        DevicePlatform platform,
        Instant createdAt,
        Instant lastUsedAt,
        boolean current) {

    public ActiveSessionSummary {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(deviceName, "deviceName");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastUsedAt, "lastUsedAt");
    }
}
