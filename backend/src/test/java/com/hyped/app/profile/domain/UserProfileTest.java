package com.hyped.app.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserProfileTest {
    private static final Instant CREATED = Instant.parse("2026-09-19T10:00:00Z");
    private static final UserId USER_ID = new UserId(UUID.fromString("12345678-1234-1234-1234-123456789abc"));

    @Test
    void requiresIdentityCiphertextRevisionAndTimestamps() {
        assertThatThrownBy(() -> profile(null, bytes(1), null, 1, CREATED, CREATED))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> profile(USER_ID, null, null, 1, CREATED, CREATED))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> profile(USER_ID, bytes(1), null, 1, null, CREATED))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> profile(USER_ID, bytes(1), null, 1, CREATED, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresPositiveRevisionAndOrderedTimestamps() {
        assertThatThrownBy(() -> profile(USER_ID, bytes(1), null, 0, CREATED, CREATED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
        assertThatThrownBy(() -> profile(USER_ID, bytes(1), null, -1, CREATED, CREATED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
        assertThatThrownBy(() -> profile(USER_ID, bytes(1), null, 1, CREATED, CREATED.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("updatedAt");
    }

    @Test
    void defensivelyCopiesCiphertextAndRedactsItFromString() {
        byte[] displayName = bytes(1);
        byte[] providerPhoto = bytes(2);
        UserProfile profile = profile(USER_ID, displayName, providerPhoto, 1, CREATED, CREATED);
        Arrays.fill(displayName, (byte) 9);
        Arrays.fill(providerPhoto, (byte) 9);

        assertThat(profile.displayNameCiphertext()).isEqualTo(bytes(1));
        assertThat(profile.providerPhotoUrlCiphertext()).isEqualTo(bytes(2));
        profile.displayNameCiphertext()[0] = 8;
        profile.providerPhotoUrlCiphertext()[0] = 8;
        assertThat(profile.displayNameCiphertext()).isEqualTo(bytes(1));
        assertThat(profile.providerPhotoUrlCiphertext()).isEqualTo(bytes(2));
        assertThat(profile.toString()).doesNotContain(Arrays.toString(bytes(1)), Arrays.toString(bytes(2)))
                .contains("ciphertext=[REDACTED]");
    }

    @Test
    void byteArrayValuesParticipateInEqualityAndHashCode() {
        UserProfile first = profile(USER_ID, bytes(1), bytes(2), 1, CREATED, CREATED);
        UserProfile equal = profile(USER_ID, bytes(1), bytes(2), 1, CREATED, CREATED);
        UserProfile different = profile(USER_ID, bytes(3), bytes(2), 1, CREATED, CREATED);

        assertThat(first).isEqualTo(equal).hasSameHashCodeAs(equal).isNotEqualTo(different);
    }

    private static UserProfile profile(UserId userId, byte[] displayName, byte[] providerPhoto,
            long revision, Instant createdAt, Instant updatedAt) {
        return new UserProfile(userId, displayName, providerPhoto, null, revision, createdAt, updatedAt);
    }

    private static byte[] bytes(int value) {
        byte[] bytes = new byte[8];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
