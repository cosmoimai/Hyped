package com.hyped.app.reminder.domain;

import com.hyped.app.room.domain.RoomId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record RoomReminder(
        UUID id,
        RoomId roomId,
        Kind kind,
        Instant eventAt,
        Instant scheduledAt,
        Status status,
        UUID claimToken,
        Instant claimedAt,
        Instant claimExpiresAt,
        Instant completedAt,
        Instant canceledAt,
        Instant createdAt,
        Instant updatedAt) {
    public enum Kind {
        DAY_BEFORE(Duration.ofHours(24)),
        HOUR_BEFORE(Duration.ofHours(1)),
        EVENT_TIME(Duration.ZERO);

        private final Duration leadTime;

        Kind(Duration leadTime) {
            this.leadTime = leadTime;
        }

        public Instant scheduledAt(Instant eventAt) {
            return eventAt.minus(leadTime);
        }
    }

    public enum Status {
        PENDING,
        CLAIMED,
        COMPLETED,
        CANCELED
    }
}
