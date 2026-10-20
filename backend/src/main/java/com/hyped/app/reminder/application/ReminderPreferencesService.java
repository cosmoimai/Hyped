package com.hyped.app.reminder.application;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.reminder.application.port.out.ReminderPreferencesRepository;
import com.hyped.app.reminder.domain.ReminderPreferences;
import com.hyped.app.room.application.RoomService;
import com.hyped.app.room.domain.RoomId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderPreferencesService {
    private final ReminderPreferencesRepository preferences;
    private final RoomService rooms;
    private final Clock clock;

    public ReminderPreferencesService(ReminderPreferencesRepository preferences, RoomService rooms, Clock clock) {
        this.preferences = preferences;
        this.rooms = rooms;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReminderPreferences get(UserId actor, RoomId roomId) {
        rooms.get(actor, roomId);
        return preferences.find(roomId, actor).orElseGet(() -> defaults(roomId, actor));
    }

    @Transactional
    public ReminderPreferences replace(UserId actor, RoomId roomId, boolean remind24HoursBefore,
            boolean remind1HourBefore, boolean remindAtEventTime) {
        rooms.get(actor, roomId);
        return preferences.upsert(roomId, actor, remind24HoursBefore, remind1HourBefore,
                remindAtEventTime, clock.instant());
    }

    @Transactional
    public void createDefaults(RoomId roomId, UserId userId) {
        preferences.createDefaults(roomId, userId, clock.instant());
    }

    private ReminderPreferences defaults(RoomId roomId, UserId userId) {
        var now = clock.instant();
        preferences.createDefaults(roomId, userId, now);
        return new ReminderPreferences(roomId, userId, true, true, true, now, now);
    }
}
