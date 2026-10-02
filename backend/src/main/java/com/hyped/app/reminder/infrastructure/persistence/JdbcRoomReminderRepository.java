package com.hyped.app.reminder.infrastructure.persistence;

import com.hyped.app.reminder.application.port.out.RoomReminderRepository;
import com.hyped.app.reminder.domain.RoomReminder;
import com.hyped.app.room.domain.RoomId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRoomReminderRepository implements RoomReminderRepository {
    private final JdbcTemplate jdbc;

    public JdbcRoomReminderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void replacePending(RoomId roomId, Instant eventAt, Instant now) {
        cancelOpen(roomId, now);
        for (RoomReminder.Kind kind : RoomReminder.Kind.values()) {
            jdbc.update("""
                    INSERT INTO app.room_reminder
                        (id, room_id, kind, event_at, scheduled_at, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'pending', ?, ?)
                    """, UUID.randomUUID(), roomId.value(), database(kind), timestamp(eventAt),
                    timestamp(kind.scheduledAt(eventAt)), timestamp(now), timestamp(now));
        }
    }

    @Override
    public int cancelOpen(RoomId roomId, Instant now) {
        return jdbc.update("""
                UPDATE app.room_reminder
                SET status = 'canceled', canceled_at = ?, updated_at = ?, claim_token = NULL,
                    claimed_at = NULL, claim_expires_at = NULL
                WHERE room_id = ? AND status IN ('pending', 'claimed')
                """, timestamp(now), timestamp(now), roomId.value());
    }

    @Override
    public List<RoomReminder> claimDue(Instant now, int limit, Duration lease) {
        UUID claimToken = UUID.randomUUID();
        return jdbc.query("""
                WITH due AS (
                    SELECT rr.id
                    FROM app.room_reminder rr
                    JOIN app.room r ON r.id = rr.room_id
                    WHERE rr.status = 'pending'
                        AND rr.scheduled_at <= ?
                        AND r.status = 'active'
                        AND r.event_at = rr.event_at
                    ORDER BY rr.scheduled_at, rr.id
                    LIMIT ?
                    FOR UPDATE OF rr SKIP LOCKED
                )
                UPDATE app.room_reminder rr
                SET status = 'claimed', claim_token = ?, claimed_at = ?, claim_expires_at = ?, updated_at = ?
                FROM due
                WHERE rr.id = due.id
                RETURNING rr.id, rr.room_id, rr.kind, rr.event_at, rr.scheduled_at, rr.status, rr.claim_token,
                    rr.claimed_at, rr.claim_expires_at, rr.completed_at, rr.canceled_at, rr.created_at, rr.updated_at
                """, this::map, timestamp(now), limit, claimToken, timestamp(now), timestamp(now.plus(lease)),
                timestamp(now));
    }

    @Override
    public boolean complete(UUID reminderId, UUID claimToken, Instant now) {
        int changed = jdbc.update("""
                UPDATE app.room_reminder
                SET status = 'completed', completed_at = COALESCE(completed_at, ?), updated_at = ?
                WHERE id = ? AND claim_token = ? AND status IN ('claimed', 'completed')
                """, timestamp(now), timestamp(now), reminderId, claimToken);
        return changed == 1;
    }

    private RoomReminder map(ResultSet rs, int row) throws SQLException {
        return new RoomReminder(rs.getObject("id", UUID.class), new RoomId(rs.getObject("room_id", UUID.class)),
                kind(rs.getString("kind")), instant(rs, "event_at"), instant(rs, "scheduled_at"),
                status(rs.getString("status")), rs.getObject("claim_token", UUID.class),
                nullableInstant(rs, "claimed_at"), nullableInstant(rs, "claim_expires_at"),
                nullableInstant(rs, "completed_at"), nullableInstant(rs, "canceled_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static RoomReminder.Kind kind(String value) {
        return switch (value) {
            case "day_before" -> RoomReminder.Kind.DAY_BEFORE;
            case "hour_before" -> RoomReminder.Kind.HOUR_BEFORE;
            case "event_time" -> RoomReminder.Kind.EVENT_TIME;
            default -> throw new IllegalArgumentException("Unknown reminder kind");
        };
    }

    private static RoomReminder.Status status(String value) {
        return RoomReminder.Status.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static String database(RoomReminder.Kind kind) {
        return switch (kind) {
            case DAY_BEFORE -> "day_before";
            case HOUR_BEFORE -> "hour_before";
            case EVENT_TIME -> "event_time";
        };
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, java.time.OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        java.time.OffsetDateTime value = rs.getObject(column, java.time.OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
