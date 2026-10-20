package com.hyped.app.notification.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface PushRegistrationRepository {
    Binding lock(UUID userId, UUID sessionId, UUID installationId, Instant now);

    void replace(Binding binding, UUID sessionId, UUID installationId,
            byte[] ciphertext, byte[] fingerprint, Instant now);

    record Binding(UUID userId, UUID deviceId, String keyReference) {
        @Override
        public String toString() {
            return "PushBinding[REDACTED]";
        }
    }
}
