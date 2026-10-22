package com.hyped.app.policy.infrastructure;

import com.hyped.app.policy.application.PolicyCatalog.Versions;
import com.hyped.app.policy.application.port.out.PolicyAcceptanceStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPolicyAcceptanceStore implements PolicyAcceptanceStore {
    private final JdbcTemplate jdbc;
    private static final RowMapper<Acceptance> MAPPER = (rs, row) -> new Acceptance(
            new Versions(rs.getString("terms_version"), rs.getString("privacy_version"),
                    rs.getString("content_rules_version")), rs.getBoolean("adult_affirmed"),
            rs.getTimestamp("accepted_at").toInstant());
    public JdbcPolicyAcceptanceStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public Optional<Acceptance> find(UUID user, Versions versions) {
        return jdbc.query("""
                SELECT * FROM app.user_policy_acceptance WHERE user_id=? AND terms_version=?
                AND privacy_version=? AND content_rules_version=?
                """, MAPPER, user, versions.termsVersion(), versions.privacyVersion(), versions.contentRulesVersion())
                .stream().findFirst();
    }
    @Override
    public Optional<Acceptance> latest(UUID user) {
        return jdbc.query("""
                SELECT * FROM app.user_policy_acceptance WHERE user_id=? ORDER BY accepted_at DESC,id DESC LIMIT 1
                """, MAPPER, user).stream().findFirst();
    }
    @Override
    public void accept(UUID user, Versions versions, Instant now) {
        jdbc.update("""
                INSERT INTO app.user_policy_acceptance(id,user_id,terms_version,privacy_version,content_rules_version,
                    adult_affirmed,accepted_at,created_at) VALUES (?,?,?,?,?,TRUE,?,?)
                ON CONFLICT (user_id,terms_version,privacy_version,content_rules_version) DO NOTHING
                """, UUID.randomUUID(), user, versions.termsVersion(), versions.privacyVersion(),
                versions.contentRulesVersion(), Timestamp.from(now), Timestamp.from(now));
    }
}
