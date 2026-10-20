package com.hyped.app.reminder.infrastructure.persistence;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.reminder.application.port.out.ReminderPreferencesRepository;
import com.hyped.app.reminder.domain.ReminderPreferences;
import com.hyped.app.room.domain.RoomId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReminderPreferencesRepository implements ReminderPreferencesRepository {
    private final JdbcTemplate jdbc;

    public JdbcReminderPreferencesRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReminderPreferences> find(RoomId roomId, UserId userId) {
        return jdbc.query("""
                SELECT room_id, user_id, remind_24_hours_before, remind_1_hour_before,
                    remind_at_event_time, created_at, updated_at
                FROM app.room_reminder_preference
                WHERE room_id = ? AND user_id = ?
                """, this::map, roomId.value(), userId.value()).stream().findFirst();
    }

    @Override
    public ReminderPreferences upsert(RoomId roomId, UserId userId, boolean remind24HoursBefore,
            boolean remind1HourBefore, boolean remindAtEventTime, Instant now) {
        jdbc.update("""
                INSERT INTO app.room_reminder_preference
                    (room_id, user_id, remind_24_hours_before, remind_1_hour_before,
                     remind_at_event_time, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (room_id, user_id) DO UPDATE SET
                    remind_24_hours_before = EXCLUDED.remind_24_hours_before,
                    remind_1_hour_before = EXCLUDED.remind_1_hour_before,
                    remind_at_event_time = EXCLUDED.remind_at_event_time,
                    updated_at = EXCLUDED.updated_at
                """, roomId.value(), userId.value(), remind24HoursBefore, remind1HourBefore,
                remindAtEventTime, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return find(roomId, userId).orElseThrow();
    }

    @Override
    public void createDefaults(RoomId roomId, UserId userId, Instant now) {
        jdbc.update("""
                INSERT INTO app.room_reminder_preference
                    (room_id, user_id, remind_24_hours_before, remind_1_hour_before,
                     remind_at_event_time, created_at, updated_at)
                VALUES (?, ?, TRUE, TRUE, TRUE, ?, ?)
                ON CONFLICT (room_id, user_id) DO NOTHING
                """, roomId.value(), userId.value(), java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));
    }

    private ReminderPreferences map(ResultSet rs, int row) throws SQLException {
        return new ReminderPreferences(
                new RoomId(rs.getObject("room_id", UUID.class)),
                new UserId(rs.getObject("user_id", UUID.class)),
                rs.getBoolean("remind_24_hours_before"),
                rs.getBoolean("remind_1_hour_before"),
                rs.getBoolean("remind_at_event_time"),
                rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant());
    }
}
