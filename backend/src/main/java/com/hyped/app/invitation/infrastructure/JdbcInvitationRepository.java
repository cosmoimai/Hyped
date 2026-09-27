package com.hyped.app.invitation.infrastructure;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.invitation.application.Invitation;
import com.hyped.app.invitation.application.port.out.InvitationRepository;
import com.hyped.app.room.domain.RoomId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcInvitationRepository implements InvitationRepository {
    private final JdbcTemplate jdbc;

    public JdbcInvitationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Invitation> find(RoomId room) {
        return jdbc.query("SELECT * FROM app.room_invitation WHERE room_id = ?", this::map, room.value())
                .stream().findFirst();
    }

    @Override
    public Optional<Invitation> findCredential(byte[] digest, boolean roomCode) {
        String column = roomCode ? "room_code_hmac" : "link_token_hash";
        return jdbc.query("SELECT * FROM app.room_invitation WHERE " + column + " = ?", this::map, digest)
                .stream().findFirst();
    }

    @Override
    public boolean replace(Invitation invitation) {
        // Delete/insert is invisible outside the transaction. ON CONFLICT avoids poisoning PostgreSQL's
        // transaction on collision. Exhausting retries rolls back the delete and restores the old generation.
        jdbc.update("DELETE FROM app.room_invitation WHERE room_id = ?", invitation.roomId().value());
        return jdbc.update("""
                INSERT INTO app.room_invitation (room_id, generation, link_token_hash, room_code_hmac,
                    link_token_ciphertext, room_code_ciphertext, created_by_user_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, invitation.roomId().value(), invitation.generation(), invitation.linkTokenHash(),
                invitation.roomCodeHmac(), invitation.linkTokenCiphertext(), invitation.roomCodeCiphertext(),
                invitation.createdByUserId().value(), Timestamp.from(invitation.createdAt()),
                Timestamp.from(invitation.updatedAt())) == 1;
    }

    @Override
    public boolean claim(long lockKey) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, lockKey));
    }

    @Override
    public Optional<Replay> replay(UserId actor, String operation, String key, Instant now) {
        jdbc.update("DELETE FROM ops.idempotency_record WHERE user_id = ? AND expires_at <= ?",
                actor.value(), Timestamp.from(now));
        return jdbc.query("""
                SELECT request_hash, response_body ->> 'ciphertext', response_status FROM ops.idempotency_record
                WHERE user_id = ? AND operation = ? AND idempotency_key = ?
                """, (rs, row) -> new Replay(rs.getBytes(1), Base64.getDecoder().decode(rs.getString(2)), rs.getInt(3)),
                actor.value(), operation, key)
                .stream().findFirst();
    }

    @Override
    public void remember(
            UserId actor, String operation, String key, byte[] request, byte[] response,
            RoomId resource, int status, Instant now, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO ops.idempotency_record
                    (user_id, operation, idempotency_key, request_hash, response_body,
                     resource_id, response_status, created_at, expires_at)
                VALUES (?, ?, ?, ?, jsonb_build_object('ciphertext', ?::text), ?, ?, ?, ?)
                """, actor.value(), operation, key, request, Base64.getEncoder().encodeToString(response),
                resource == null ? null : resource.value(), status, Timestamp.from(now), Timestamp.from(expiresAt));
    }

    private Invitation map(ResultSet rs, int row) throws SQLException {
        return new Invitation(new RoomId(rs.getObject("room_id", UUID.class)), rs.getInt("generation"),
                rs.getBytes("link_token_hash"), rs.getBytes("room_code_hmac"), rs.getBytes("link_token_ciphertext"),
                rs.getBytes("room_code_ciphertext"), new UserId(rs.getObject("created_by_user_id", UUID.class)),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
