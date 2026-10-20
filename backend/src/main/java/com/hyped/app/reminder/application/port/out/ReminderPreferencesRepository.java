package com.hyped.app.reminder.application.port.out;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.reminder.domain.ReminderPreferences;
import com.hyped.app.room.domain.RoomId;
import java.time.Instant;
import java.util.Optional;

public interface ReminderPreferencesRepository {
    Optional<ReminderPreferences> find(RoomId roomId, UserId userId);

    ReminderPreferences upsert(
            RoomId roomId,
            UserId userId,
            boolean remind24HoursBefore,
            boolean remind1HourBefore,
            boolean remindAtEventTime,
            Instant now);

    void createDefaults(RoomId roomId, UserId userId, Instant now);
}
