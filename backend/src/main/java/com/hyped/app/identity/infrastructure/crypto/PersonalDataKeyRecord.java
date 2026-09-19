package com.hyped.app.identity.infrastructure.crypto;

import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException.Reason;
import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

record PersonalDataKeyRecord(
        UserId userId,
        String keyReference,
        String environment,
        String kmsKeyName,
        byte[] wrappedDek,
        PersonalDataKeyStatus status,
        Instant createdAt,
        Instant destroyedAt) {

    PersonalDataKeyRecord {
        if (userId == null || status == null || createdAt == null || invalid(keyReference, 255)
                || invalid(environment, 255) || invalid(kmsKeyName, 1024)) {
            throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
        }
        if ((status == PersonalDataKeyStatus.ACTIVE) != (wrappedDek != null && destroyedAt == null)) {
            throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
        }
        if (status == PersonalDataKeyStatus.DESTROYED && (wrappedDek != null || destroyedAt == null)) {
            throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
        }
        if (destroyedAt != null && destroyedAt.isBefore(createdAt)) {
            throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
        }
        wrappedDek = copy(wrappedDek);
    }

    @Override
    public byte[] wrappedDek() {
        return copy(wrappedDek);
    }

    PersonalDataKeyRecord destroyed(Instant at) {
        Objects.requireNonNull(at, "at");
        return new PersonalDataKeyRecord(userId, keyReference, environment, kmsKeyName, null,
                PersonalDataKeyStatus.DESTROYED, createdAt, at);
    }

    @Override
    public String toString() {
        return "PersonalDataKeyRecord[status=" + status + ", protected values=[REDACTED]]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PersonalDataKeyRecord that)) {
            return false;
        }
        return userId.equals(that.userId) && keyReference.equals(that.keyReference)
                && environment.equals(that.environment) && kmsKeyName.equals(that.kmsKeyName)
                && Arrays.equals(wrappedDek, that.wrappedDek) && status == that.status
                && createdAt.equals(that.createdAt) && Objects.equals(destroyedAt, that.destroyedAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(userId, keyReference, environment, kmsKeyName, status, createdAt, destroyedAt);
        return 31 * result + Arrays.hashCode(wrappedDek);
    }

    private static boolean invalid(String value, int maximumLength) {
        return value == null || value.isBlank() || value.length() > maximumLength;
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }
}
