package com.hyped.app.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RefreshTokenRecordTest {

    private static final Instant ISSUED = Instant.parse("2026-09-15T10:00:00Z");
    private static final Instant EXPIRES = ISSUED.plusSeconds(60);
    private static final RefreshTokenId ID = new RefreshTokenId(UUID.randomUUID());
    private static final SessionId SESSION = new SessionId(UUID.randomUUID());
    private static final RefreshTokenId REPLACEMENT = new RefreshTokenId(UUID.randomUUID());

    @ParameterizedTest
    @ValueSource(ints = {0, 31, 33})
    void rejectsIncorrectDigestLength(int length) {
        assertThatIllegalArgumentException().isThrownBy(() -> token(
                new byte[length], RefreshTokenState.ACTIVE, null, EXPIRES, null));
    }

    @Test
    void rejectsMissingDigest() {
        assertThatNullPointerException().isThrownBy(() -> token(null, RefreshTokenState.ACTIVE, null, EXPIRES, null));
    }

    @Test
    void activeTokensCannotHaveConsumptionHistory() {
        assertThatIllegalArgumentException().isThrownBy(() -> token(
                new byte[32], RefreshTokenState.ACTIVE, ISSUED, EXPIRES, null));
        assertThatIllegalArgumentException().isThrownBy(() -> token(
                new byte[32], RefreshTokenState.ACTIVE, null, EXPIRES, REPLACEMENT));
    }

    @Test
    void consumedTokensRequireConsumptionTimeAndReplacement() {
        assertThatIllegalArgumentException().isThrownBy(() -> token(
                new byte[32], RefreshTokenState.CONSUMED, null, EXPIRES, REPLACEMENT));
        assertThatIllegalArgumentException().isThrownBy(() -> token(
                new byte[32], RefreshTokenState.CONSUMED, ISSUED, EXPIRES, null));
        assertThat(token(new byte[32], RefreshTokenState.CONSUMED, ISSUED, EXPIRES, REPLACEMENT).state())
                .isEqualTo(RefreshTokenState.CONSUMED);
    }

    @Test
    void revokedTokensMayHaveBeenUsedOrUnused() {
        assertThat(token(new byte[32], RefreshTokenState.REVOKED, null, EXPIRES, null).consumedAt()).isNull();
        RefreshTokenRecord used = token(new byte[32], RefreshTokenState.REVOKED, ISSUED, EXPIRES, REPLACEMENT);
        assertThat(used.consumedAt()).isEqualTo(ISSUED);
        assertThat(used.replacedById()).isEqualTo(REPLACEMENT);
    }

    @Test
    void validatesExpiryAndConsumptionTimeBounds() {
        assertThatIllegalArgumentException().isThrownBy(() -> token(
                new byte[32], RefreshTokenState.ACTIVE, null, ISSUED, null));
        assertThatIllegalArgumentException().isThrownBy(() -> token(
                new byte[32], RefreshTokenState.ACTIVE, null, ISSUED.minusNanos(1), null));
        assertThatIllegalArgumentException().isThrownBy(() -> token(
                new byte[32], RefreshTokenState.CONSUMED, ISSUED.minusNanos(1), EXPIRES, REPLACEMENT));
        assertThatIllegalArgumentException().isThrownBy(() -> token(
                new byte[32], RefreshTokenState.REVOKED, EXPIRES.plusNanos(1), EXPIRES, REPLACEMENT));
    }

    @Test
    void copiesDigestWithoutLeakingItThroughToStringOrChangingEquality() {
        byte[] input = new byte[32];
        RefreshTokenRecord token = token(input, RefreshTokenState.ACTIVE, null, EXPIRES, null);
        RefreshTokenRecord equal = token(input, RefreshTokenState.ACTIVE, null, EXPIRES, null);
        input[0] = 99;
        token.tokenDigest()[0] = 88;

        assertThat(token.tokenDigest()).containsExactly(new byte[32]);
        assertThat(token).isEqualTo(equal).hasSameHashCodeAs(equal);
        assertThat(token.toString()).isEqualTo(
                "RefreshTokenRecord[id=" + ID + ", sessionId=" + SESSION + ", state=ACTIVE]");
    }

    private static RefreshTokenRecord token(byte[] digest, RefreshTokenState state,
            Instant consumed, Instant expires, RefreshTokenId replacedBy) {
        return new RefreshTokenRecord(ID, SESSION, digest, state, ISSUED, consumed, expires, replacedBy, ISSUED);
    }
}
