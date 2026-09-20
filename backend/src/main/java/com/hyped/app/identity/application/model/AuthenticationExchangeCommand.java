package com.hyped.app.identity.application.model;

import com.hyped.app.identity.domain.DevicePlatform;
import com.hyped.app.identity.domain.InstallationId;
import java.util.Objects;

public record AuthenticationExchangeCommand(
        String firebaseIdToken,
        InstallationId installationId,
        DevicePlatform platform,
        String deviceName,
        String appVersion) {

    public AuthenticationExchangeCommand {
        Objects.requireNonNull(firebaseIdToken, "firebaseIdToken");
        Objects.requireNonNull(installationId, "installationId");
        Objects.requireNonNull(platform, "platform");
        deviceName = normalized(deviceName, 80, "deviceName");
        appVersion = normalized(appVersion, 64, "appVersion");
    }

    private static String normalized(String value, int maximum, String field) {
        Objects.requireNonNull(value, field);
        String result = value.strip();
        if (result.isBlank() || result.codePointCount(0, result.length()) > maximum) {
            throw new IllegalArgumentException(field + " must contain between 1 and " + maximum + " characters");
        }
        return result;
    }

    @Override
    public String toString() {
        return "AuthenticationExchangeCommand[firebaseIdToken=[REDACTED], installationId=" + installationId
                + ", platform=" + platform + ", deviceName=" + deviceName + ", appVersion=" + appVersion + "]";
    }
}
