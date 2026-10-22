package com.hyped.app.policy;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Existing room/reminder fixtures model adults who already accepted the current documents. */
public final class AcceptedPolicyFixture {
    private AcceptedPolicyFixture() { }
    public static void accept(JdbcTemplate jdbc, UUID user) {
        jdbc.update("""
                INSERT INTO app.user_policy_acceptance(id,user_id,terms_version,privacy_version,content_rules_version,
                    adult_affirmed,accepted_at,created_at)
                VALUES (?,?,'2026-10-22','2026-10-22','2026-10-22',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), user);
    }
}
