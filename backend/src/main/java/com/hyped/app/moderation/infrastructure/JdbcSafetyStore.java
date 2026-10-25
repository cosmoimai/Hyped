package com.hyped.app.moderation.infrastructure;

import com.hyped.app.moderation.application.port.out.SafetyStore;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcSafetyStore implements SafetyStore {
    private final JdbcTemplate jdbc;

    public JdbcSafetyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean active(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM app.app_user WHERE id=? AND status='active')",
                        Boolean.class,
                        id));
    }

    @Override
    public String keyReference(UUID id) {
        return jdbc.queryForObject(
                "SELECT pii_key_reference FROM app.app_user WHERE id=?", String.class, id);
    }

    @Override
    public boolean linked(UUID room, UUID user) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM app.room_member WHERE room_id=? AND"
                                + " user_id=?)",
                        Boolean.class,
                        room,
                        user));
    }

    @Override
    public boolean shared(UUID actor, UUID target) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
                        SELECT EXISTS(SELECT 1 FROM app.room_member a JOIN app.room_member b ON b.room_id=a.room_id
                        WHERE a.user_id=? AND b.user_id=?)
                        """,
                        Boolean.class,
                        actor,
                        target));
    }

    @Override
    public boolean blocked(UUID first, UUID second) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
                        SELECT EXISTS(SELECT 1 FROM app.user_block WHERE
                        (blocker_user_id=? AND blocked_user_id=?) OR (blocker_user_id=? AND blocked_user_id=?))
                        """,
                        Boolean.class,
                        first,
                        second,
                        second,
                        first));
    }

    @Override
    public Set<UUID> hidden(UUID actor, UUID room) {
        return new HashSet<>(
                jdbc.queryForList(
                        """
                        SELECT m.user_id FROM app.room_member m WHERE m.room_id=? AND EXISTS
                        (SELECT 1 FROM app.user_block b WHERE (b.blocker_user_id=? AND b.blocked_user_id=m.user_id)
                        OR (b.blocked_user_id=? AND b.blocker_user_id=m.user_id))
                        """,
                        UUID.class,
                        room,
                        actor,
                        actor));
    }

    @Override
    public boolean ownerConflict(UUID room, UUID owner) {
        return !hidden(owner, room).isEmpty();
    }

    @Override
    public Replay replay(UUID actor, UUID key, String operation) {
        var values =
                jdbc.query(
                        """
                        SELECT request_digest,resource_id,removed_count,left_count FROM ops.safety_request
                        WHERE user_id=? AND request_key=? AND operation=?
                        """,
                        (r, n) ->
                                new Replay(
                                        r.getBytes(1),
                                        r.getObject(2, UUID.class),
                                        r.getInt(3),
                                        r.getInt(4)),
                        actor,
                        key,
                        operation);
        return values.isEmpty() ? null : values.getFirst();
    }

    @Override
    public void remember(
            UUID actor,
            UUID key,
            String operation,
            byte[] digest,
            UUID resource,
            int removed,
            int left,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO ops.safety_request(user_id,request_key,operation,request_digest,resource_id,
                removed_count,left_count,created_at) VALUES (?,?,?,?,?,?,?,?)
                """,
                actor,
                key,
                operation,
                digest,
                resource,
                removed,
                left,
                Timestamp.from(now));
    }

    @Override
    public int recentReports(UUID actor, Instant since) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM app.report WHERE reporter_user_id=? AND created_at>?",
                Integer.class,
                actor,
                Timestamp.from(since));
    }

    @Override
    public void insertReport(Report r, byte[] fingerprint, Instant now) {
        jdbc.update(
                """
                INSERT INTO app.report(id,room_id,reporter_user_id,subject_user_id,media_asset_id,
                subject_fingerprint,subject_type,reason_code,details_ciphertext,retain_until,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                r.id(),
                r.roomId(),
                r.reporter(),
                r.subjectUser(),
                r.mediaId(),
                fingerprint,
                r.subjectType(),
                r.reason(),
                r.details(),
                Timestamp.from(r.retainUntil()),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    @Override
    public List<Block> blocks(UUID actor, UUID after) {
        return jdbc.query(
                """
                SELECT blocked_user_id,created_at FROM app.user_block WHERE blocker_user_id=?
                AND blocked_user_id>? ORDER BY blocked_user_id LIMIT 101
                """,
                (r, n) -> new Block(r.getObject(1, UUID.class), r.getTimestamp(2).toInstant()),
                actor,
                after == null ? new UUID(0, 0) : after);
    }

    @Override
    public boolean ownBlock(UUID actor, UUID target) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
                        SELECT EXISTS(SELECT 1 FROM app.user_block WHERE blocker_user_id=? AND blocked_user_id=?)
                        """,
                        Boolean.class,
                        actor,
                        target));
    }

    @Override
    public void insertBlock(UUID actor, UUID target, Instant now) {
        jdbc.update(
                "INSERT INTO app.user_block(id,blocker_user_id,blocked_user_id,created_at) VALUES"
                        + " (?,?,?,?)",
                UUID.randomUUID(),
                actor,
                target,
                Timestamp.from(now));
    }

    @Override
    public void unblock(UUID actor, UUID target) {
        jdbc.update(
                "DELETE FROM app.user_block WHERE blocker_user_id=? AND blocked_user_id=?",
                actor,
                target);
    }

    @Override
    public List<UUID> sharedRooms(UUID actor, UUID target) {
        return jdbc.queryForList(
                """
                SELECT a.room_id FROM app.room_member a JOIN app.room_member b ON b.room_id=a.room_id
                WHERE a.user_id=? AND b.user_id=? ORDER BY a.room_id
                """,
                UUID.class,
                actor,
                target);
    }

    @Override
    public void invalidate(UUID actor, UUID room, Instant now) {
        jdbc.update(
                """
                INSERT INTO ops.room_access_invalidation(user_id,room_id,version,created_at) VALUES (?,?,?,?)
                ON CONFLICT(user_id,room_id) DO UPDATE SET version=EXCLUDED.version,created_at=EXCLUDED.created_at
                """,
                actor,
                room,
                UUID.randomUUID(),
                Timestamp.from(now));
    }

    @Override
    public List<Invalidation> invalidations(UUID actor) {
        return jdbc.query(
                """
                SELECT room_id,version FROM ops.room_access_invalidation WHERE user_id=?
                ORDER BY created_at,room_id LIMIT 100
                """,
                (r, n) -> new Invalidation(r.getObject(1, UUID.class), r.getObject(2, UUID.class)),
                actor);
    }

    @Override
    public void acknowledge(UUID actor, UUID version) {
        jdbc.update(
                "DELETE FROM ops.room_access_invalidation WHERE user_id=? AND version=?",
                actor,
                version);
    }

    private static final String REPORT =
            """
            SELECT r.*,u.pii_key_reference FROM app.report r
            LEFT JOIN app.app_user u ON u.id=r.reporter_user_id
            """;

    @Override
    public List<Report> queue(int limit, Instant now) {
        return jdbc.query(
                REPORT
                        + " WHERE r.status='OPEN' AND r.retain_until>? ORDER BY r.created_at,r.id"
                        + " LIMIT ?",
                JdbcSafetyStore::map,
                Timestamp.from(now),
                limit);
    }

    @Override
    public Report report(UUID id, boolean lock) {
        var rows =
                jdbc.query(
                        REPORT + " WHERE r.id=?" + (lock ? " FOR UPDATE OF r" : ""),
                        JdbcSafetyStore::map,
                        id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static Report map(ResultSet r, int n) throws SQLException {
        return new Report(
                r.getObject("id", UUID.class),
                r.getObject("room_id", UUID.class),
                r.getObject("reporter_user_id", UUID.class),
                r.getObject("subject_user_id", UUID.class),
                r.getObject("media_asset_id", UUID.class),
                r.getString("subject_type"),
                r.getString("reason_code"),
                r.getBytes("details_ciphertext"),
                r.getString("pii_key_reference"),
                r.getString("status"),
                r.getString("resolution"),
                r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("retain_until").toInstant());
    }

    @Override
    public void resolve(UUID id, String action, byte[] reviewer, Instant now) {
        jdbc.update(
                """
                UPDATE app.report SET status=?,resolution=?,reviewer_digest=?,resolved_at=?,updated_at=? WHERE id=?
                """,
                action.equals("DISMISS") ? "DISMISSED" : "ACTIONED",
                action,
                reviewer,
                Timestamp.from(now),
                Timestamp.from(now),
                id);
        jdbc.update(
                "INSERT INTO ops.moderation_audit(id,report_id,reviewer_digest,action,created_at)"
                        + " VALUES (?,?,?,?,?)",
                UUID.randomUUID(),
                id,
                reviewer,
                action,
                Timestamp.from(now));
    }

    @Override
    public List<UUID> photoRooms(UUID media) {
        return jdbc.queryForList(
                "SELECT room_id FROM app.room_theme WHERE media_asset_id=? ORDER BY room_id",
                UUID.class,
                media);
    }

    @Override
    public List<UUID> lockPhoto(UUID media) {
        jdbc.queryForList(
                "SELECT id FROM app.media_asset WHERE id=? FOR UPDATE", UUID.class, media);
        return photoRooms(media);
    }

    @Override
    public boolean held(UUID room) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT moderation_hold FROM app.room WHERE id=?", Boolean.class, room));
    }

    @Override
    public void hold(UUID room, boolean held) {
        jdbc.update("UPDATE app.room SET moderation_hold=? WHERE id=?", held, room);
    }

    @Override
    public void hidePhoto(UUID media, Instant now) {
        // The caller locks referencing rooms in stable order before this update, as in cover edits.
        jdbc.update(
                """
                UPDATE app.room SET revision=revision+1,updated_at=? WHERE id IN
                (SELECT room_id FROM app.room_theme WHERE media_asset_id=?)
                """,
                Timestamp.from(now),
                media);
        jdbc.update(
                """
                UPDATE app.room_theme SET kind='preset',preset_key='soft-blue-01',media_asset_id=NULL,
                overlay_key='dark-soft',updated_at=? WHERE media_asset_id=?
                """,
                Timestamp.from(now),
                media);
        jdbc.update(
                "UPDATE app.media_asset SET status='DELETING',updated_at=? WHERE id=?",
                Timestamp.from(now),
                media);
    }

    @Override
    public void suspend(UUID user, Instant now) {
        jdbc.update(
                "UPDATE app.app_user SET status='suspended',updated_at=? WHERE id=? AND"
                        + " status IN ('active','locked','compromised','suspended')",
                Timestamp.from(now),
                user);
        jdbc.update(
                """
                UPDATE app.auth_session SET revoked_at=?,revoke_reason='moderation',updated_at=?
                WHERE user_id=? AND revoked_at IS NULL
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                user);
        jdbc.update(
                """
                UPDATE app.refresh_token_record SET state='revoked' WHERE session_id IN
                (SELECT id FROM app.auth_session WHERE user_id=?) AND state='active'
                """,
                user);
        jdbc.update(
                "UPDATE app.device_registration SET invalidated_at=?,updated_at=? WHERE user_id=?",
                Timestamp.from(now),
                Timestamp.from(now),
                user);
        jdbc.update(
                """
                UPDATE app.reminder_delivery SET status='canceled',updated_at=?
                WHERE user_id=? AND status IN ('pending','sending')
                """,
                Timestamp.from(now),
                user);
    }

    @Override
    public boolean allowReview(byte[] reviewer, Instant now) {
        long minute = Math.floorDiv(now.getEpochSecond(), 60);
        jdbc.update("DELETE FROM ops.moderation_rate WHERE minute<?", minute - 2);
        Integer attempts =
                jdbc.queryForObject(
                        """
                        INSERT INTO ops.moderation_rate(reviewer_digest,minute,attempts) VALUES (?,?,1)
                        ON CONFLICT(reviewer_digest,minute) DO UPDATE
                        SET attempts=LEAST(ops.moderation_rate.attempts+1,61) RETURNING attempts
                        """,
                        Integer.class,
                        reviewer,
                        minute);
        return attempts != null && attempts <= 60;
    }

    @Override
    public void cleanup(Instant now) {
        jdbc.update("DELETE FROM app.report WHERE retain_until<=?", Timestamp.from(now));
        jdbc.update(
                "DELETE FROM ops.safety_request WHERE created_at<?",
                Timestamp.from(now.minusSeconds(86400)));
    }
}
