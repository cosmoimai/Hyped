package com.hyped.app.identity.application.model;

import com.hyped.app.identity.domain.DevicePlatform;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.UserId;
import java.util.Objects;

public record CreateSessionCommand(
        UserId userId,
        InstallationId installationId,
        DevicePlatform platform,
        String deviceName,
        byte[] fcmTokenCiphertext,
        byte[] fcmTokenFingerprint,
        boolean notificationsEnabled) {

    public CreateSessionCommand {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(installationId, "installationId");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(deviceName, "deviceName");
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

    @Override
    public String toString() {
        return "CreateSessionCommand[userId=" + userId + ", installationId=" + installationId
                + ", platform=" + platform + ", deviceName=" + deviceName
                + ", notificationsEnabled=" + notificationsEnabled + "]";
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }
}
