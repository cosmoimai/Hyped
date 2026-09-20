package com.hyped.app.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException.Reason;
import com.hyped.app.identity.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AesGcmEnvelopeCipherTest {
    private final AesGcmEnvelopeCipher cipher = new AesGcmEnvelopeCipher();
    private final SecretKey key = new SecretKeySpec(HmacSha256IdentityLookupProtectorTest.key(), "AES");
    private final UserId userId = new UserId(UUID.fromString("12345678-1234-1234-1234-123456789abc"));
    private final EncryptionContext context = new EncryptionContext("test", userId, "app.user_identity", "email");

    @Test
    void binaryRoundTripWithFreshNonceVersionAndIndependentArrays() {
        byte[] plaintext = new byte[]{0, -1, 127, -128, 1};
        byte[] original = plaintext.clone();
        byte[] encrypted = cipher.encrypt(key, plaintext, context);
        byte[] second = cipher.encrypt(key, plaintext, context);
        assertThat(encrypted[0]).isEqualTo((byte) 1);
        assertThat(encrypted).hasSize(1 + 12 + 16 + plaintext.length).isNotEqualTo(second);
        assertThat(Arrays.copyOfRange(encrypted, 1, 13)).isNotEqualTo(Arrays.copyOfRange(second, 1, 13));
        Arrays.fill(plaintext, (byte) 33);
        byte[] decrypted = cipher.decrypt(key, encrypted, context);
        assertThat(decrypted).isEqualTo(original);
        Arrays.fill(decrypted, (byte) 66);
        assertThat(cipher.decrypt(key, encrypted, context)).isEqualTo(original);
        assertThat(context).isEqualTo(new EncryptionContext("test", userId, "app.user_identity", "email"));
        assertThat(context.toString()).doesNotContain("test", userId.toString(), "app.user_identity", "email");
    }

    @Test
    void emptyPlaintextRoundTrip() {
        assertThat(cipher.decrypt(key, cipher.encrypt(key, new byte[0], context), context)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 13, 17})
    void rejectsModifiedNonceCiphertextAndTag(int offset) {
        byte[] envelope = cipher.encrypt(key, new byte[4], context);
        envelope[offset] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(key, envelope, context)).hasMessage("AUTHENTICATION_FAILED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "environment", "table", "column"})
    void rejectsWrongContext(String field) {
        var wrong = new EncryptionContext(field.equals("environment") ? "production" : context.environment(),
                field.equals("user") ? new UserId(UUID.randomUUID()) : userId,
                field.equals("table") ? "app.profile" : context.table(),
                field.equals("column") ? "display_name" : context.column());
        byte[] encrypted = cipher.encrypt(key, new byte[1], context);
        assertThatThrownBy(() -> cipher.decrypt(key, encrypted, wrong)).hasMessage("AUTHENTICATION_FAILED");
    }

    @Test
    void rejectsWrongKeyAndDoesNotLeakAnyValues() {
        byte[] plaintext = "sensitive plaintext".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = cipher.encrypt(key, plaintext, context);
        SecretKey wrong = new SecretKeySpec(new byte[32], "AES");
        assertThatThrownBy(() -> cipher.decrypt(wrong, encrypted, context))
                .isInstanceOfSatisfying(PersonalDataProtectionException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(Reason.AUTHENTICATION_FAILED);
                    assertThat(exception.getMessage()).isEqualTo("AUTHENTICATION_FAILED");
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.toString()).doesNotContain("sensitive plaintext", "test", userId.toString(),
                            "app.user_identity", "email", Arrays.toString(encrypted),
                            Arrays.toString(key.getEncoded()));
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 12, 13, 28})
    void rejectsTruncatedEnvelope(int length) {
        assertThatThrownBy(() -> cipher.decrypt(key, new byte[length], context)).hasMessage("MALFORMED_ENVELOPE");
    }

    @Test
    void rejectsUnsupportedVersionAndInvalidInputs() {
        byte[] encrypted = cipher.encrypt(key, new byte[0], context);
        encrypted[0] = 2;
        assertThatThrownBy(() -> cipher.decrypt(key, encrypted, context)).hasMessage("UNSUPPORTED_VERSION");
        assertThatThrownBy(() -> cipher.encrypt(key, null, context)).hasMessage("INVALID_INPUT");
        assertThatThrownBy(() -> cipher.encrypt(key, new byte[0], null)).hasMessage("INVALID_INPUT");
        assertThatThrownBy(() -> cipher.encrypt(null, new byte[0], context)).hasMessage("KEY_UNAVAILABLE");
        assertThatThrownBy(() -> cipher.encrypt(new SecretKeySpec(new byte[16], "AES"), new byte[0], context))
                .hasMessage("KEY_UNAVAILABLE");
        assertThatThrownBy(() -> new EncryptionContext(null, userId, "table", "column")).hasMessage("INVALID_INPUT");
    }

    @Test
    void associatedDataIsLengthPrefixed() {
        var first = new EncryptionContext("a", userId, "bc", "d");
        var second = new EncryptionContext("a", userId, "b", "cd");
        byte[] aad = first.associatedData(1);
        assertThat(aad).isNotEqualTo(second.associatedData(1)).isNotEqualTo(first.associatedData(2));
        Arrays.fill(aad, (byte) 0);
        assertThat(first.associatedData(1)).isNotEqualTo(aad);
    }
}
