package com.hyped.app.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AuthSessionTest {

    private static final Instant ISSUED = Instant.parse("2026-09-15T10:00:00Z");

    @ParameterizedTest
    @CsvSource({"0, 0", "0, -1", "-1, 60", "61, 60"})
    void rejectsInvalidExpiryAndActivityTimes(long lastUsedOffset, long expiryOffset) {
        assertThatIllegalArgumentException().isThrownBy(() -> session(
                ISSUED.plusSeconds(lastUsedOffset), ISSUED.plusSeconds(expiryOffset), null, null, ISSUED));
    }

    @Test
    void validatesRevocationPairAndReasonLength() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> session(ISSUED, ISSUED.plusSeconds(60), ISSUED, null, ISSUED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> session(ISSUED, ISSUED.plusSeconds(60), null, "logout", ISSUED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> session(ISSUED, ISSUED.plusSeconds(60), ISSUED, "", ISSUED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> session(ISSUED, ISSUED.plusSeconds(60), ISSUED, "x".repeat(33), ISSUED));
    }

    @Test
    void rejectsRevocationBeforeIssueAndAuditTimeBeforeCreation() {
        assertThatIllegalArgumentException().isThrownBy(() -> session(
                ISSUED, ISSUED.plusSeconds(60), ISSUED.minusNanos(1), "logout", ISSUED));
        assertThatIllegalArgumentException().isThrownBy(() -> session(
                ISSUED, ISSUED.plusSeconds(60), null, null, ISSUED.minusNanos(1)));
    }

    @Test
    void allowsNullableDeviceAndRevocationAfterExpiry() {
        AuthSession session = session(ISSUED.plusSeconds(60), ISSUED.plusSeconds(60),
                ISSUED.plusSeconds(90), "logout", ISSUED.plusSeconds(90));

        assertThat(session.deviceId()).isNull();
        assertThat(session.revokeReason()).isEqualTo("logout");
    }

    private static AuthSession session(
            Instant lastUsed, Instant expires, Instant revoked, String reason, Instant updated) {
        return new AuthSession(new SessionId(UUID.randomUUID()), new UserId(UUID.randomUUID()), null,
                new TokenFamilyId(UUID.randomUUID()), ISSUED, lastUsed, expires, revoked, reason, ISSUED, updated);
    }
}
