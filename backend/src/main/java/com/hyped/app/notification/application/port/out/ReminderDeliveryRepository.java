package com.hyped.app.notification.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReminderDeliveryRepository {
    int prepare(int limit, Instant now);

    List<Delivery> claim(int limit, Instant now);

    boolean stillEligible(Delivery delivery, Instant now);

    void finish(Delivery delivery, PushSender.Outcome outcome, Instant now);

    record Delivery(UUID id, UUID claimToken, UUID roomId, UUID userId, UUID deviceId, UUID sessionId,
            String kind, long revision, String keyReference, byte[] ciphertext, byte[] fingerprint, int attempts) {
        @Override
        public String toString() {
            return "ReminderDelivery[REDACTED]";
        }
    }
}
