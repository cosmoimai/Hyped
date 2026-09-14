package com.hyped.app.identity.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public record DeviceRegistration(
        DeviceId id,
        UserId userId,
        DevicePlatform platform,
        InstallationId installationId,
        String deviceName,
        byte[] fcmTokenCiphertext,
        byte[] fcmTokenFingerprint,
        boolean notificationsEnabled,
        Instant lastSeenAt,
        Instant invalidatedAt,
        Instant createdAt,
        Instant updatedAt) {

    public DeviceRegistration {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(installationId, "installationId");
        Objects.requireNonNull(deviceName, "deviceName");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        deviceName = deviceName.strip();
        int nameLength = deviceName.codePointCount(0, deviceName.length());
        if (nameLength < 1 || nameLength > 80) {
            throw new IllegalArgumentException("deviceName must contain 1 to 80 characters after trimming");
        }
        if ((fcmTokenCiphertext == null) != (fcmTokenFingerprint == null)) {
            throw new IllegalArgumentException("FCM values must both be present or both absent");
        }
        if (fcmTokenFingerprint != null && fcmTokenFingerprint.length != 32) {
            throw new IllegalArgumentException("FCM fingerprint must contain exactly 32 bytes");
        }
        if (notificationsEnabled && fcmTokenCiphertext == null) {
            throw new IllegalArgumentException("Notifications require FCM values");
        }
        if (updatedAt.isBefore(createdAt) || lastSeenAt.isBefore(createdAt)
                || (invalidatedAt != null && invalidatedAt.isBefore(createdAt))) {
            throw new IllegalArgumentException("Device timestamps cannot be before createdAt");
        }
        fcmTokenCiphertext = copy(fcmTokenCiphertext);
        fcmTokenFingerprint = copy(fcmTokenFingerprint);
    }

    @Override
    public byte[] fcmTokenCiphertext() {
        return copy(fcmTokenCiphertext);
    }

    @Override
    public byte[] fcmTokenFingerprint() {
        return copy(fcmTokenFingerprint);
    }

    public boolean isActive() {
        return invalidatedAt == null;
    }

    @Override
    public String toString() {
        return "DeviceRegistration[id=" + id + ", userId=" + userId + ", platform=" + platform
                + ", active=" + isActive() + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DeviceRegistration that)) {
            return false;
        }
        return notificationsEnabled == that.notificationsEnabled
                && id.equals(that.id) && userId.equals(that.userId) && platform == that.platform
                && installationId.equals(that.installationId) && deviceName.equals(that.deviceName)
                && Arrays.equals(fcmTokenCiphertext, that.fcmTokenCiphertext)
                && Arrays.equals(fcmTokenFingerprint, that.fcmTokenFingerprint)
                && lastSeenAt.equals(that.lastSeenAt) && Objects.equals(invalidatedAt, that.invalidatedAt)
                && createdAt.equals(that.createdAt) && updatedAt.equals(that.updatedAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, userId, platform, installationId, deviceName, notificationsEnabled,
                lastSeenAt, invalidatedAt, createdAt, updatedAt);
        result = 31 * result + Arrays.hashCode(fcmTokenCiphertext);
        return 31 * result + Arrays.hashCode(fcmTokenFingerprint);
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }
}
