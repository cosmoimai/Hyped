package com.hyped.app.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class UserAccountTest {

    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final UserId USER_ID = new UserId(UUID.fromString("019b1f20-4152-7ce8-ae9e-b12b87fe8a31"));

    @Test
    void activeAccountCanStartSession() {
        assertThat(account(AccountStatus.ACTIVE, null, 0).canStartSession(NOW)).isTrue();
    }

    @Test
    void fiveFailuresLockAccountForFifteenMinutes() {
        UserAccount original = account(AccountStatus.ACTIVE, null, 0);
        UserAccount current = original;
        for (int count = 1; count <= 5; count++) {
            Instant attemptAt = NOW.plusSeconds(count);
            current = current.recordAttributableFailure(attemptAt, 5, LOCK_DURATION);
            assertThat(current.failedAuthCount()).isEqualTo(count);
            assertThat(current.updatedAt()).isEqualTo(attemptAt);
            if (count < 5) {
                assertThat(current.status()).isEqualTo(AccountStatus.ACTIVE);
                assertThat(current.lockedUntil()).isNull();
            }
        }

        assertThat(current.status()).isEqualTo(AccountStatus.LOCKED);
        assertThat(current.lockedUntil()).isEqualTo(NOW.plusSeconds(5).plus(LOCK_DURATION));
        assertThat(current.canStartSession(NOW.plusSeconds(5))).isFalse();
        assertThat(current.id()).isEqualTo(original.id());
        assertThat(current.createdAt()).isEqualTo(original.createdAt());
        assertThat(current.piiKeyReference()).isEqualTo(original.piiKeyReference());
        assertThat(original.failedAuthCount()).isZero();
        assertThat(original.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void lockRejectsAuthenticationUntilExactExpiryWithoutMutatingState() {
        Instant expiry = NOW.plus(LOCK_DURATION);
        UserAccount locked = account(AccountStatus.LOCKED, expiry, 5);

        assertThat(locked.canStartSession(expiry.minusNanos(1))).isFalse();
        assertThatIllegalStateException()
                .isThrownBy(() -> locked.recordSuccessfulAuthentication(expiry.minusNanos(1)));
        assertThatIllegalStateException()
                .isThrownBy(() -> locked.recordAttributableFailure(NOW, 5, LOCK_DURATION));
        assertThat(locked.canStartSession(expiry)).isTrue();
        assertThat(locked.canStartSession(expiry.plusSeconds(1))).isTrue();
        assertThat(locked.status()).isEqualTo(AccountStatus.LOCKED);
        assertThat(locked.lockedUntil()).isEqualTo(expiry);
    }

    @Test
    void successfulAuthenticationExplicitlyClearsExpiredLockAndFailures() {
        Instant expiry = NOW.plus(LOCK_DURATION);
        UserAccount locked = account(AccountStatus.LOCKED, expiry, 5);

        UserAccount authenticated = locked.recordSuccessfulAuthentication(expiry);

        assertThat(authenticated.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(authenticated.lockedUntil()).isNull();
        assertThat(authenticated.failedAuthCount()).isZero();
        assertThat(authenticated.updatedAt()).isEqualTo(expiry);
        assertThat(locked.status()).isEqualTo(AccountStatus.LOCKED);
        assertThat(locked.failedAuthCount()).isEqualTo(5);
    }

    @Test
    void successResetsActiveAccountFailures() {
        UserAccount authenticated = account(AccountStatus.ACTIVE, null, 4)
                .recordSuccessfulAuthentication(NOW.plusSeconds(1));

        assertThat(authenticated.failedAuthCount()).isZero();
        assertThat(authenticated.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void failureAfterLockExpiryRetainsFailureHistoryAndRelocks() {
        Instant expiry = NOW.plus(LOCK_DURATION);
        UserAccount locked = account(AccountStatus.LOCKED, expiry, 5);

        UserAccount failed = locked.recordAttributableFailure(expiry, 5, LOCK_DURATION);

        assertThat(failed.failedAuthCount()).isEqualTo(6);
        assertThat(failed.lockedUntil()).isEqualTo(expiry.plus(LOCK_DURATION));
        assertThat(failed.status()).isEqualTo(AccountStatus.LOCKED);
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"SUSPENDED", "COMPROMISED", "DELETION_PENDING", "DELETED"})
    void deniedStatesCannotStartSessionsOrBeReactivatedByAuthentication(AccountStatus status) {
        UserAccount denied = account(status, null, 0);

        assertThat(denied.canStartSession(NOW)).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> denied.recordSuccessfulAuthentication(NOW));
        assertThatIllegalStateException()
                .isThrownBy(() -> denied.recordAttributableFailure(NOW, 5, LOCK_DURATION));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveThreshold(int threshold) {
        assertThatIllegalArgumentException().isThrownBy(() -> account(AccountStatus.ACTIVE, null, 0)
                .recordAttributableFailure(NOW, threshold, LOCK_DURATION));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveLockDuration(long nanos) {
        assertThatIllegalArgumentException().isThrownBy(() -> account(AccountStatus.ACTIVE, null, 0)
                .recordAttributableFailure(NOW, 5, Duration.ofNanos(nanos)));
    }

    @Test
    void rejectsMissingTimeAndDuration() {
        UserAccount active = account(AccountStatus.ACTIVE, null, 0);

        assertThatNullPointerException().isThrownBy(() -> active.canStartSession(null));
        assertThatNullPointerException().isThrownBy(() -> active.recordSuccessfulAuthentication(null));
        assertThatNullPointerException().isThrownBy(() -> active.recordAttributableFailure(null, 5, LOCK_DURATION));
        assertThatNullPointerException().isThrownBy(() -> active.recordAttributableFailure(NOW, 5, null));
    }

    @Test
    void rejectsNegativeFailuresAndMissingLockExpiry() {
        assertThatIllegalArgumentException().isThrownBy(() -> account(AccountStatus.ACTIVE, null, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> account(AccountStatus.LOCKED, null, 5));
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = "LOCKED", mode = EnumSource.Mode.EXCLUDE)
    void onlyLockedAccountsCanHaveLockExpiry(AccountStatus status) {
        assertThatIllegalArgumentException().isThrownBy(() -> account(status, NOW.plus(LOCK_DURATION), 0));
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = "DELETED", mode = EnumSource.Mode.EXCLUDE)
    void onlyDeletedAccountsCanHaveDeletionCompletionTimestamps(AccountStatus status) {
        Instant expiry = status == AccountStatus.LOCKED ? NOW.plus(LOCK_DURATION) : null;

        assertThatIllegalArgumentException().isThrownBy(() -> new UserAccount(
                USER_ID, status, expiry, 0, null, null, NOW, null, NOW, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new UserAccount(
                USER_ID, status, expiry, 0, null, null, null, NOW, NOW, NOW));
    }

    @Test
    void deletedAccountsRequireBothCompletionTimestamps() {
        assertThatIllegalArgumentException().isThrownBy(() -> new UserAccount(
                USER_ID, AccountStatus.DELETED, null, 0, null, NOW, null, NOW, NOW, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new UserAccount(
                USER_ID, AccountStatus.DELETED, null, 0, null, NOW, NOW, null, NOW, NOW));
    }

    @Test
    void timestampsCannotMoveBackwards() {
        assertThatIllegalArgumentException().isThrownBy(() -> new UserAccount(
                USER_ID, AccountStatus.ACTIVE, null, 0, null, null, null, null, NOW, NOW.minusNanos(1)));
        UserAccount active = account(AccountStatus.ACTIVE, null, 0);
        assertThatIllegalArgumentException().isThrownBy(() -> active.recordSuccessfulAuthentication(NOW.minusNanos(1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> active.recordAttributableFailure(NOW.minusNanos(1), 5, LOCK_DURATION));
    }

    private static UserAccount account(AccountStatus status, Instant lockedUntil, int failures) {
        Instant deletedAt = status == AccountStatus.DELETED ? NOW : null;
        return new UserAccount(USER_ID, status, lockedUntil, failures, "key-reference",
                null, deletedAt, deletedAt, NOW, NOW);
    }
}
