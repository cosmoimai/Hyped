package com.hyped.app.notification.infrastructure;

import com.hyped.app.notification.application.port.out.PushSender;
import com.hyped.app.notification.application.port.out.ReminderDeliveryRepository;
import com.hyped.app.reminder.application.port.out.RoomReminderRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcReminderDeliveryRepository implements ReminderDeliveryRepository {
    private final JdbcTemplate jdbc;
    private final RoomReminderRepository reminders;

    public JdbcReminderDeliveryRepository(JdbcTemplate jdbc, RoomReminderRepository reminders) {
        this.jdbc = jdbc;
        this.reminders = reminders;
    }

    // This predicate is shared by preparation, cancellation, and send-time claiming.
    private static final String ELIGIBLE = """
            u.status = 'active' AND d.platform = 'android' AND d.invalidated_at IS NULL
            AND d.notifications_enabled AND d.fcm_token_ciphertext IS NOT NULL
            AND s.revoked_at IS NULL AND s.expires_at > ? AND d.push_session_id = s.id
            AND r.status = 'active' AND r.event_at = rr.event_at AND rr.status <> 'canceled'
            AND rr.scheduled_at >= rr.created_at AND rr.scheduled_at >= m.joined_at
            AND rr.scheduled_at >= COALESCE(p.updated_at, m.joined_at)
            AND CASE rr.kind
                WHEN 'day_before' THEN COALESCE(p.remind_24_hours_before, TRUE)
                WHEN 'hour_before' THEN COALESCE(p.remind_1_hour_before, TRUE)
                WHEN 'event_time' THEN COALESCE(p.remind_at_event_time, TRUE)
                ELSE FALSE END
            """;

    @Override
    @Transactional
    public int prepare(int limit, Instant now) {
        // Expiry has no update event, so prune it when the scheduler ticks.
        jdbc.update("""
                UPDATE app.device_registration d SET fcm_token_ciphertext = NULL, fcm_token_fingerprint = NULL,
                    notifications_enabled = FALSE, push_session_id = NULL
                WHERE d.push_session_id IS NOT NULL AND NOT EXISTS (
                    SELECT 1 FROM app.auth_session s JOIN app.app_user u ON u.id = s.user_id
                    WHERE s.id = d.push_session_id AND s.revoked_at IS NULL AND s.expires_at > ?
                        AND u.status = 'active')
                """, Timestamp.from(now));
        var due = reminders.claimDue(now, limit, java.time.Duration.ofMinutes(5));
        int inserted = 0;
        for (var reminder : due) {
            inserted += jdbc.update("""
                    INSERT INTO app.reminder_delivery
                        (id, reminder_id, room_id, device_id, session_id, user_id, kind, event_at,
                         available_at, created_at, updated_at)
                    SELECT gen_random_uuid(), rr.id, r.id, d.id, s.id, u.id, rr.kind, rr.event_at, ?, ?, ?
                    FROM app.room_reminder rr JOIN app.room r ON r.id = rr.room_id
                    JOIN app.room_member m ON m.room_id = r.id
                    JOIN app.app_user u ON u.id = m.user_id
                    JOIN app.device_registration d ON d.user_id = u.id
                    JOIN app.auth_session s ON s.id = d.push_session_id AND s.user_id = u.id AND s.device_id = d.id
                    LEFT JOIN app.room_reminder_preference p ON p.room_id = r.id AND p.user_id = u.id
                    WHERE rr.id = ? AND rr.claim_token = ? AND rr.status = 'claimed' AND
                    """ + ELIGIBLE + " ON CONFLICT (room_id, kind, event_at, device_id) DO NOTHING",
                    Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), reminder.id(),
                    reminder.claimToken(), Timestamp.from(now));
            reminders.complete(reminder.id(), reminder.claimToken(), now);
        }
        return inserted;
    }

    private static final String DELIVERY_JOINS = """
            FROM app.reminder_delivery pd
            JOIN app.room_reminder rr ON rr.id = pd.reminder_id
            JOIN app.room r ON r.id = pd.room_id
            JOIN app.app_user u ON u.id = pd.user_id
            JOIN app.device_registration d ON d.id = pd.device_id AND d.user_id = u.id
            JOIN app.auth_session s ON s.id = pd.session_id AND s.user_id = u.id AND s.device_id = d.id
            JOIN app.room_member m ON m.room_id = r.id AND m.user_id = u.id
            LEFT JOIN app.room_reminder_preference p ON p.room_id = r.id AND p.user_id = u.id
            """;

    @Override
    @Transactional
    public List<Delivery> claim(int limit, Instant now) {
        jdbc.update("""
                UPDATE app.reminder_delivery target SET status = 'canceled', updated_at = ?
                WHERE target.status IN ('pending', 'sending') AND NOT EXISTS (SELECT 1
                """ + DELIVERY_JOINS + " WHERE pd.id = target.id AND " + ELIGIBLE + ")",
                Timestamp.from(now), Timestamp.from(now));
        // Bound retries and recover crashed leases. A stable notificationId also deduplicates on Android.
        jdbc.update("""
                UPDATE app.reminder_delivery SET status = 'failed', updated_at = ?
                WHERE attempts >= 5 AND status IN ('pending', 'sending') AND (lease_until IS NULL OR lease_until <= ?)
                """, Timestamp.from(now), Timestamp.from(now));
        UUID claim = UUID.randomUUID();
        jdbc.update("""
                WITH due AS (
                    SELECT id FROM app.reminder_delivery WHERE attempts < 5 AND available_at <= ?
                        AND (status = 'pending' OR (status = 'sending' AND lease_until <= ?))
                    ORDER BY available_at, id LIMIT ? FOR UPDATE SKIP LOCKED
                ) UPDATE app.reminder_delivery pd
                SET status = 'sending', attempts = attempts + 1, claim_token = ?, lease_until = ?, updated_at = ?
                FROM due WHERE pd.id = due.id
                """, Timestamp.from(now), Timestamp.from(now), limit, claim, Timestamp.from(now.plusSeconds(300)),
                Timestamp.from(now));
        return jdbc.query("""
                SELECT pd.id, pd.room_id, pd.user_id, pd.device_id, pd.session_id, pd.kind, pd.attempts,
                    r.revision, u.pii_key_reference, d.fcm_token_ciphertext, d.fcm_token_fingerprint
                """ + DELIVERY_JOINS + " WHERE pd.claim_token = ? AND pd.status = 'sending' AND " + ELIGIBLE,
                (rs, row) -> new Delivery(rs.getObject("id", UUID.class), claim, rs.getObject("room_id", UUID.class),
                        rs.getObject("user_id", UUID.class), rs.getObject("device_id", UUID.class),
                        rs.getObject("session_id", UUID.class), rs.getString("kind"), rs.getLong("revision"),
                        rs.getString("pii_key_reference"), rs.getBytes("fcm_token_ciphertext"),
                        rs.getBytes("fcm_token_fingerprint"), rs.getInt("attempts")), claim, Timestamp.from(now));
    }

    @Override
    public boolean stillEligible(Delivery delivery, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 " + DELIVERY_JOINS
                + " WHERE pd.id = ? AND pd.claim_token = ? AND pd.status = 'sending' AND " + ELIGIBLE
                + " AND d.fcm_token_fingerprint = ?)", Boolean.class, delivery.id(), delivery.claimToken(),
                Timestamp.from(now), delivery.fingerprint()));
    }

    @Override
    @Transactional
    public void finish(Delivery delivery, PushSender.Outcome outcome, Instant now) {
        String status = switch (outcome) {
            case SENT -> "sent";
            case CANCELED -> "canceled";
            case RETRY -> delivery.attempts() < 5 ? "pending" : "failed";
            default -> "failed";
        };
        jdbc.update("""
                UPDATE app.reminder_delivery SET status = ?, available_at = ?, updated_at = ?
                WHERE id = ? AND claim_token = ? AND status = 'sending'
                """, status, Timestamp.from(now.plusSeconds(60L << Math.min(delivery.attempts() - 1, 4))),
                Timestamp.from(now), delivery.id(), delivery.claimToken());
        if (outcome == PushSender.Outcome.INVALID_TOKEN) {
            // A delayed failure for the replaced token must not delete a newer token.
            jdbc.update("""
                    UPDATE app.device_registration SET fcm_token_ciphertext = NULL, fcm_token_fingerprint = NULL,
                        notifications_enabled = FALSE, push_session_id = NULL
                    WHERE id = ? AND push_session_id = ? AND fcm_token_fingerprint = ?
                    """, delivery.deviceId(), delivery.sessionId(), delivery.fingerprint());
        }
    }
}
