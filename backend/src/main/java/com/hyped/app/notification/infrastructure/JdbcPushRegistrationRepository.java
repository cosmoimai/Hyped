package com.hyped.app.notification.infrastructure;

import com.hyped.app.common.api.ApiProblemException;
import com.hyped.app.notification.application.port.out.PushRegistrationRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPushRegistrationRepository implements PushRegistrationRepository {
    private final JdbcTemplate jdbc;

    public JdbcPushRegistrationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Binding lock(UUID user, UUID session, UUID installation, Instant now) {
        var result = jdbc.query("""
                SELECT d.id, u.pii_key_reference FROM app.app_user u
                JOIN app.auth_session s ON s.user_id = u.id
                JOIN app.device_registration d ON d.id = s.device_id AND d.user_id = u.id
                WHERE u.id = ? AND u.status = 'active' AND s.id = ? AND s.revoked_at IS NULL
                    AND s.expires_at > ? AND d.installation_id = ? AND d.invalidated_at IS NULL
                    AND d.platform = 'android'
                FOR UPDATE OF u, s, d
                """, (rs, row) -> new Binding(user, rs.getObject("id", UUID.class), rs.getString("pii_key_reference")),
                user, session, Timestamp.from(now), installation);
        if (result.isEmpty()) {
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Session unavailable", "Sign in again before registering notifications.");
        }
        return result.getFirst();
    }

    @Override
    public void replace(Binding binding, UUID session, UUID installation,
            byte[] ciphertext, byte[] fingerprint, Instant now) {
        // One installation/token must never stay attached to the previous account.
        if (fingerprint != null) {
            jdbc.update("""
                    UPDATE app.device_registration SET fcm_token_ciphertext = NULL, fcm_token_fingerprint = NULL,
                        notifications_enabled = FALSE, push_session_id = NULL
                    WHERE id <> ? AND (installation_id = ? OR fcm_token_fingerprint = ?)
                    """, binding.deviceId(), installation, fingerprint);
        }
        jdbc.update("""
                UPDATE app.device_registration SET fcm_token_ciphertext = ?, fcm_token_fingerprint = ?,
                    notifications_enabled = ?, push_session_id = ?, updated_at = GREATEST(updated_at, ?)
                WHERE id = ? AND user_id = ?
                """, ciphertext, fingerprint, ciphertext != null, ciphertext == null ? null : session,
                Timestamp.from(now), binding.deviceId(), binding.userId());
    }
}
