package com.hyped.app.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UserIdentityTest {

    private static final IdentityId ID = new IdentityId(UUID.randomUUID());
    private static final UserId USER = new UserId(UUID.randomUUID());
    private static final Instant LINKED = Instant.parse("2026-09-17T10:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"id", "userId", "provider", "subject", "linked", "verified", "created"})
    void rejectsNullRequiredFields(String field) {
        assertThatNullPointerException().isThrownBy(() -> new UserIdentity(
                field.equals("id") ? null : ID, field.equals("userId") ? null : USER,
                field.equals("provider") ? null : IdentityProvider.GOOGLE,
                field.equals("subject") ? null : new byte[32], null, null, false,
                field.equals("linked") ? null : LINKED, field.equals("verified") ? null : LINKED,
                field.equals("created") ? null : LINKED));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 31, 33})
    void requiresThirtyTwoByteSubjectHmac(int length) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> identity(new byte[length], null, null, false));
    }

    @Test
    void requiresEmailFieldsTogether() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> identity(new byte[32], new byte[] {1}, null, false));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> identity(new byte[32], null, new byte[32], false));
        UserIdentity absent = identity(new byte[32], null, null, false);
        assertThat(absent.emailCiphertext()).isNull();
        assertThat(absent.emailHmac()).isNull();
        assertThat(identity(new byte[32], new byte[] {1}, new byte[32], false).emailVerified()).isFalse();
    }

    @Test
    void verifiedEmailRequiresProtectedEmailData() {
        assertThatIllegalArgumentException().isThrownBy(() -> identity(new byte[32], null, null, true));
        assertThat(identity(new byte[32], new byte[] {1}, new byte[32], true).emailVerified()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 31, 33})
    void requiresThirtyTwoByteEmailHmac(int length) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> identity(new byte[32], new byte[] {1}, new byte[length], false));
    }

    @Test
    void validatesTimestampOrder() {
        assertThatIllegalArgumentException().isThrownBy(() -> new UserIdentity(ID, USER, IdentityProvider.GOOGLE,
                new byte[32], null, null, false, LINKED, LINKED.minusNanos(1), LINKED));
        assertThatIllegalArgumentException().isThrownBy(() -> new UserIdentity(ID, USER, IdentityProvider.GOOGLE,
                new byte[32], null, null, false, LINKED, LINKED, LINKED.plusNanos(1)));
        UserIdentity valid = new UserIdentity(ID, USER, IdentityProvider.GOOGLE, new byte[32], null, null, false,
                LINKED, LINKED.plusSeconds(1), LINKED.minusSeconds(1));
        assertThat(valid.lastVerifiedAt()).isEqualTo(LINKED.plusSeconds(1));
    }

    @Test
    void copiesAllArraysOnConstructionAndAccessAndUsesContentEquality() {
        byte[] subject = new byte[32];
        byte[] ciphertext = {11, 12, 13};
        byte[] email = new byte[32];
        UserIdentity identity = identity(subject, ciphertext, email, true);
        UserIdentity equal = identity(subject.clone(), ciphertext.clone(), email.clone(), true);
        int hash = identity.hashCode();
        subject[0] = 99;
        ciphertext[0] = 99;
        email[0] = 99;
        identity.providerSubjectHmac()[0] = 88;
        identity.emailCiphertext()[0] = 88;
        identity.emailHmac()[0] = 88;

        assertThat(identity.providerSubjectHmac()).containsExactly(new byte[32]);
        assertThat(identity.emailCiphertext()).containsExactly(11, 12, 13);
        assertThat(identity.emailHmac()).containsExactly(new byte[32]);
        assertThat(identity).isEqualTo(equal).hasSameHashCodeAs(equal);
        assertThat(identity.hashCode()).isEqualTo(hash);
        assertThat(identity).isNotEqualTo(identity(subject, new byte[] {11, 12, 13}, new byte[32], true));
        assertThat(identity).isNotEqualTo(identity(new byte[32], ciphertext, new byte[32], true));
        assertThat(identity).isNotEqualTo(identity(new byte[32], new byte[] {11, 12, 13}, email, true));
        assertThat(identity(new byte[32], null, null, false))
                .isEqualTo(identity(new byte[32], null, null, false));
    }

    @Test
    void toStringContainsOnlySafeIdentifiersAndProvider() {
        UserIdentity identity = identity(new byte[32], new byte[] {11, 12, 13}, new byte[32], true);
        assertThat(identity.toString()).isEqualTo(
                "UserIdentity[id=" + ID + ", userId=" + USER + ", provider=GOOGLE]");
    }

    private static UserIdentity identity(byte[] subject, byte[] ciphertext, byte[] email, boolean verified) {
        return new UserIdentity(ID, USER, IdentityProvider.GOOGLE, subject, ciphertext, email, verified,
                LINKED, LINKED, LINKED);
    }
}
