package com.hyped.app.reminder.domain;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.room.domain.RoomId;
import java.time.Instant;

public record ReminderPreferences(
        RoomId roomId,
        UserId userId,
        boolean remind24HoursBefore,
        boolean remind1HourBefore,
        boolean remindAtEventTime,
        Instant createdAt,
        Instant updatedAt) {
}
