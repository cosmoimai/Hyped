package com.hyped.app.profile.domain;

import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record UserProfile(
        UserId userId,
        byte[] displayNameCiphertext,
        byte[] providerPhotoUrlCiphertext,
        UUID photoMediaId,
        long profileRevision,
        Instant createdAt,
        Instant updatedAt) {

    public UserProfile {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(displayNameCiphertext, "displayNameCiphertext");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (profileRevision <= 0) {
            throw new IllegalArgumentException("profileRevision must be positive");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
        displayNameCiphertext = displayNameCiphertext.clone();
        providerPhotoUrlCiphertext = copy(providerPhotoUrlCiphertext);
    }

    @Override
    public byte[] displayNameCiphertext() {
        return displayNameCiphertext.clone();
    }

    @Override
    public byte[] providerPhotoUrlCiphertext() {
        return copy(providerPhotoUrlCiphertext);
    }

    @Override
    public String toString() {
        return "UserProfile[userId=" + userId + ", profileRevision=" + profileRevision
                + ", photoMediaId=" + photoMediaId + ", ciphertext=[REDACTED], createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserProfile that)) {
            return false;
        }
        return profileRevision == that.profileRevision && userId.equals(that.userId)
                && Arrays.equals(displayNameCiphertext, that.displayNameCiphertext)
                && Arrays.equals(providerPhotoUrlCiphertext, that.providerPhotoUrlCiphertext)
                && Objects.equals(photoMediaId, that.photoMediaId) && createdAt.equals(that.createdAt)
                && updatedAt.equals(that.updatedAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(userId, photoMediaId, profileRevision, createdAt, updatedAt);
        result = 31 * result + Arrays.hashCode(displayNameCiphertext);
        return 31 * result + Arrays.hashCode(providerPhotoUrlCiphertext);
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }
}
