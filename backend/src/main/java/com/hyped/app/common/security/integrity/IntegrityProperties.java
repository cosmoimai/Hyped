package com.hyped.app.common.security.integrity;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hyped.integrity")
public record IntegrityProperties(Mode mode, String packageName, Set<String> certificateDigests) {
    public enum Mode { DISABLED, OBSERVE, ENFORCE }

    public IntegrityProperties {
        mode = mode == null ? Mode.OBSERVE : mode;
        packageName = packageName == null ? "com.hyped.hyped" : packageName;
        certificateDigests = certificateDigests == null ? Set.of() : Set.copyOf(certificateDigests);
        if (!packageName.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")) {
            throw new IllegalArgumentException("Integrity package configuration is invalid");
        }
        if (certificateDigests.stream().anyMatch(value -> !value.matches("[A-Za-z0-9_-]{43}"))) {
            throw new IllegalArgumentException("Integrity certificates must be unpadded base64url SHA-256 digests");
        }
        if (mode == Mode.ENFORCE && certificateDigests.isEmpty()) {
            throw new IllegalArgumentException("Integrity enforcement requires approved signing certificates");
        }
    }
}
