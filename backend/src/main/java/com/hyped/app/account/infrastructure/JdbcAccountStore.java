package com.hyped.app.account.infrastructure;

import com.hyped.app.account.application.port.out.AccountStore;
import com.hyped.app.common.api.ApiProblemException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAccountStore implements AccountStore {
    private final JdbcTemplate jdbc;
    public JdbcAccountStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Readiness readiness(UUID userId) {
        var owned = jdbc.query("SELECT id,title FROM app.room WHERE owner_user_id = ? ORDER BY id",
                (rs, row) -> new OwnedRoom(rs.getObject("id", UUID.class), rs.getString("title")), userId);
        int joined = jdbc.queryForObject("SELECT count(*) FROM app.room_member WHERE user_id = ? AND role <> 'owner'",
                Integer.class, userId);
        return new Readiness(owned.isEmpty() && joined == 0, owned, joined);
    }
    @Override
    public boolean activeSession(UUID userId, UUID sessionId, UUID installationId, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM app.auth_session s JOIN app.device_registration d ON d.id = s.device_id
                WHERE s.id = ? AND s.user_id = ? AND d.user_id = ? AND d.installation_id = ?
                AND s.revoked_at IS NULL AND s.expires_at > ? AND d.invalidated_at IS NULL)
                """, Boolean.class, sessionId, userId, userId, installationId, Timestamp.from(now)));
    }
    @Override
    public int activeDevices(UUID userId, Instant now) {
        return jdbc.queryForObject("""
                SELECT count(DISTINCT d.id) FROM app.device_registration d JOIN app.auth_session s ON s.device_id = d.id
                WHERE d.user_id = ? AND d.invalidated_at IS NULL AND s.revoked_at IS NULL AND s.expires_at > ?
                """, Integer.class, userId, Timestamp.from(now));
    }
    @Override
    public int activeRooms(UUID userId, boolean owned) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM app.room r JOIN app.room_member m ON m.room_id = r.id
                WHERE m.user_id = ? AND r.status = 'active' AND
                """ + (owned ? " m.role = 'owner'" : " m.role <> 'owner'"), Integer.class, userId);
    }
    @Override
    public String keyReference(UUID userId) {
        return jdbc.queryForObject("SELECT pii_key_reference FROM app.app_user WHERE id = ?", String.class, userId);
    }
    @Override
    @Transactional
    public void begin(UUID userId, String keyReference, byte[] firebaseUid, Instant now) {
        String status = jdbc.queryForObject("SELECT status FROM app.app_user WHERE id = ? FOR UPDATE",
                String.class, userId);
        if (!"active".equals(status) && !"locked".equals(status)) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN,
                    "deletion_pending".equals(status) ? "ACCOUNT_DELETION_PENDING"
                            : "deleted".equals(status) ? "ACCOUNT_DELETED" : "ACCOUNT_UNAVAILABLE",
                    "Account unavailable",
                    "This account is unavailable.");
        }
        var ready = readiness(userId);
        if (!ready.ownedRooms().isEmpty()) {
            throw conflict("OWNERSHIP_TRANSFER_REQUIRED", "Transfer ownership or permanently delete all owned rooms.");
        }
        if (ready.joinedRoomCount() != 0) {
            throw conflict("ROOMS_MUST_BE_LEFT", "Leave all joined rooms before deleting your account.");
        }
        jdbc.update("""
                INSERT INTO ops.account_deletion_job(user_id,firebase_uid_ciphertext,available_at,created_at)
                VALUES (?,?,?,?)
                """, userId, firebaseUid, Timestamp.from(now), Timestamp.from(now));
        int changed = jdbc.update("""
                UPDATE app.app_user SET status='deletion_pending', locked_until=NULL,
                    deletion_requested_at=?, updated_at=? WHERE id=? AND pii_key_reference=?
                """, Timestamp.from(now), Timestamp.from(now), userId, keyReference);
        if (changed != 1) {
            throw conflict("ACCOUNT_CHANGED", "The account changed. Reload it before trying again.");
        }
        jdbc.update("""
                UPDATE app.auth_session SET revoked_at=?, revoke_reason='account_deleted', updated_at=?
                WHERE user_id=? AND revoked_at IS NULL
                """, Timestamp.from(now), Timestamp.from(now), userId);
        jdbc.update("""
                UPDATE app.refresh_token_record SET state='revoked' WHERE session_id IN
                (SELECT id FROM app.auth_session WHERE user_id=?) AND state='active'
                """, userId);
        jdbc.update("UPDATE app.device_registration SET invalidated_at=?, updated_at=? WHERE user_id=?",
                Timestamp.from(now), Timestamp.from(now), userId);
    }
    @Override
    public List<UUID> due(Instant now) {
        return jdbc.queryForList("""
                SELECT user_id FROM ops.account_deletion_job WHERE available_at <= ?
                    AND (lease_until IS NULL OR lease_until <= ?) ORDER BY available_at,created_at,user_id LIMIT 20
                """, UUID.class, Timestamp.from(now), Timestamp.from(now));
    }
    @Override
    @Transactional
    public Job claim(UUID userId, Instant now) {
        UUID claim = UUID.randomUUID();
        var jobs = jdbc.query("""
                UPDATE ops.account_deletion_job SET claim_token=?, lease_until=?
                WHERE user_id=? AND available_at <= ? AND (lease_until IS NULL OR lease_until <= ?)
                RETURNING firebase_uid_ciphertext,firebase_deleted
                """, (rs, row) -> new Job(userId, claim, keyReference(userId),
                        rs.getBytes(1), rs.getBoolean(2)), claim, Timestamp.from(now.plusSeconds(300)), userId,
                Timestamp.from(now), Timestamp.from(now));
        return jobs.isEmpty() ? null : jobs.getFirst();
    }
    @Override
    public boolean firebaseDeleted(Job job) {
        return jdbc.update("""
                UPDATE ops.account_deletion_job SET firebase_deleted=TRUE WHERE user_id=? AND claim_token=?
                """,
                job.userId(), job.claim()) == 1;
    }
    @Override
    @Transactional
    public void finish(Job job, Instant now) {
        var claims = jdbc.queryForList("""
                SELECT user_id FROM ops.account_deletion_job WHERE user_id=? AND claim_token=? FOR UPDATE
                """, UUID.class, job.userId(), job.claim());
        if (claims.isEmpty()) { return; }
        jdbc.update("DELETE FROM app.device_registration WHERE user_id=?", job.userId());
        jdbc.update("DELETE FROM app.auth_session WHERE user_id=?", job.userId());
        jdbc.update("DELETE FROM app.user_identity WHERE user_id=?", job.userId());
        jdbc.update("DELETE FROM app.user_profile WHERE user_id=?", job.userId());
        jdbc.update("DELETE FROM ops.idempotency_record WHERE user_id=?", job.userId());
        jdbc.update("DELETE FROM app.room_reminder_preference WHERE user_id=?", job.userId());
        jdbc.update("""
                UPDATE app.app_user SET status='deleted', pii_key_reference=NULL, failed_auth_count=0,
                    pii_destroyed_at=?, deleted_at=?, updated_at=? WHERE id=? AND status='deletion_pending'
                """, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), job.userId());
        jdbc.update("DELETE FROM ops.account_deletion_job WHERE user_id=? AND claim_token=?",
                job.userId(), job.claim());
    }
    @Override
    public void retry(Job job, Instant now) {
        jdbc.update("""
                UPDATE ops.account_deletion_job SET available_at=?, lease_until=NULL, claim_token=NULL
                WHERE user_id=? AND claim_token=?
                """, Timestamp.from(now.plusSeconds(60)), job.userId(), job.claim());
    }
    private static ApiProblemException conflict(String code, String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, code, "Account deletion unavailable", detail);
    }
}
