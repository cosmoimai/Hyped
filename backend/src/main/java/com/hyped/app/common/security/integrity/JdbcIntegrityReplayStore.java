package com.hyped.app.common.security.integrity;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcIntegrityReplayStore implements IntegrityReplayStore {
    private final JdbcTemplate jdbc;

    public JdbcIntegrityReplayStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(String tokenDigest, String requestDigest, Instant now) {
        jdbc.update("DELETE FROM ops.integrity_replay WHERE expires_at < ?", Timestamp.from(now));
        return jdbc.update("""
                INSERT INTO ops.integrity_replay(token_digest, request_digest, expires_at)
                VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """, tokenDigest, requestDigest, Timestamp.from(now.plusSeconds(600))) == 1;
    }

    @Override
    public boolean allow(String sessionDigest, Instant now) {
        long window = now.getEpochSecond() / 60;
        jdbc.update("DELETE FROM ops.integrity_rate WHERE minute < ?", window - 2);
        Integer count = jdbc.queryForObject("""
                INSERT INTO ops.integrity_rate(session_digest, minute, attempts) VALUES (?, ?, 1)
                ON CONFLICT (session_digest, minute) DO UPDATE
                SET attempts = LEAST(ops.integrity_rate.attempts + 1, 61) RETURNING attempts
                """, Integer.class, sessionDigest, window);
        return count != null && count <= 60;
    }
}
