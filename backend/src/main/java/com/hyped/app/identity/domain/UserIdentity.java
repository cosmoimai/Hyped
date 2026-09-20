package com.hyped.app.identity.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public record UserIdentity(
        IdentityId id,
        UserId userId,
        IdentityProvider provider,
        byte[] providerSubjectHmac,
        byte[] emailCiphertext,
        byte[] emailHmac,
        boolean emailVerified,
        Instant linkedAt,
        Instant lastVerifiedAt,
        Instant createdAt) {

    public UserIdentity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerSubjectHmac, "providerSubjectHmac");
        Objects.requireNonNull(linkedAt, "linkedAt");
        Objects.requireNonNull(lastVerifiedAt, "lastVerifiedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (providerSubjectHmac.length != 32) {
            throw new IllegalArgumentException("providerSubjectHmac must contain exactly 32 bytes");
        }
        if ((emailCiphertext == null) != (emailHmac == null)) {
            throw new IllegalArgumentException("Email ciphertext and HMAC must both be present or both absent");
        }
        if (emailHmac != null && emailHmac.length != 32) {
            throw new IllegalArgumentException("emailHmac must contain exactly 32 bytes");
        }
        if (emailVerified && emailCiphertext == null) {
            throw new IllegalArgumentException("Verified email requires protected email data");
        }
        if (lastVerifiedAt.isBefore(linkedAt)) {
            throw new IllegalArgumentException("lastVerifiedAt cannot be before linkedAt");
        }
        if (createdAt.isAfter(linkedAt)) {
            throw new IllegalArgumentException("createdAt cannot be after linkedAt");
        }
        providerSubjectHmac = providerSubjectHmac.clone();
        emailCiphertext = copy(emailCiphertext);
        emailHmac = copy(emailHmac);
    }

    @Override
    public byte[] providerSubjectHmac() {
        return providerSubjectHmac.clone();
    }

    @Override
    public byte[] emailCiphertext() {
        return copy(emailCiphertext);
    }

    @Override
    public byte[] emailHmac() {
        return copy(emailHmac);
    }

    @Override
    public String toString() {
        return "UserIdentity[id=" + id + ", userId=" + userId + ", provider=" + provider + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserIdentity that)) {
            return false;
        }
        return id.equals(that.id) && userId.equals(that.userId) && provider == that.provider
                && Arrays.equals(providerSubjectHmac, that.providerSubjectHmac)
                && Arrays.equals(emailCiphertext, that.emailCiphertext) && Arrays.equals(emailHmac, that.emailHmac)
                && emailVerified == that.emailVerified && linkedAt.equals(that.linkedAt)
                && lastVerifiedAt.equals(that.lastVerifiedAt) && createdAt.equals(that.createdAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, userId, provider, emailVerified, linkedAt, lastVerifiedAt, createdAt);
        result = 31 * result + Arrays.hashCode(providerSubjectHmac);
        result = 31 * result + Arrays.hashCode(emailCiphertext);
        return 31 * result + Arrays.hashCode(emailHmac);
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }
}
