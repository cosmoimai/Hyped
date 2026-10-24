package com.hyped.app.common.security.integrity;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;

public final class IntegrityVerifier {
    public enum Outcome { VALID, MISSING, INVALID, REPLAYED, UNAVAILABLE }
    private final IntegrityProperties properties;
    private final IntegrityDecoder decoder;
    private final IntegrityReplayStore replay;
    private final Clock clock;

    public IntegrityVerifier(IntegrityProperties properties, IntegrityDecoder decoder,
            IntegrityReplayStore replay, Clock clock) {
        this.properties = properties;
        this.decoder = decoder;
        this.replay = replay;
        this.clock = clock;
    }

    public Outcome verify(String evidence, String expectedHash, long requestTime) {
        if (evidence == null || evidence.isBlank()) {
            return Outcome.MISSING;
        }
        if (evidence.length() > 32768 || !evidence.matches("[A-Za-z0-9._-]+")
                || !fresh(requestTime)) {
            return Outcome.INVALID;
        }
        try {
            JsonNode payload = decoder.decode(evidence);
            JsonNode details = payload.path("requestDetails");
            JsonNode app = payload.path("appIntegrity");
            if (!properties.packageName().equals(details.path("requestPackageName").asText())
                    || !expectedHash.equals(details.path("requestHash").asText())
                    || !fresh(details.path("timestampMillis").asLong(0))
                    || !properties.packageName().equals(app.path("packageName").asText())
                    || !"PLAY_RECOGNIZED".equals(app.path("appRecognitionVerdict").asText())
                    || !contains(app.path("certificateSha256Digest"), properties.certificateDigests())
                    || !contains(payload.path("deviceIntegrity").path("deviceRecognitionVerdict"),
                            java.util.Set.of("MEETS_DEVICE_INTEGRITY"))
                    || !"LICENSED".equals(payload.path("accountDetails").path("appLicensingVerdict").asText())) {
                return Outcome.INVALID;
            }
            return replay.claim(digest(evidence.getBytes(StandardCharsets.UTF_8)), expectedHash, clock.instant())
                    ? Outcome.VALID : Outcome.REPLAYED;
        } catch (Exception ignored) {
            return Outcome.UNAVAILABLE;
        }
    }

    private boolean fresh(long timestamp) {
        long now = clock.millis();
        return timestamp >= now - 120000 && timestamp <= now + 10000;
    }

    private static boolean contains(JsonNode array, java.util.Set<String> values) {
        if (!array.isArray()) {
            return false;
        }
        for (JsonNode item : array) {
            if (values.contains(item.asText())) {
                return true;
            }
        }
        return false;
    }

    public static String digest(byte[] bytes) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required digest unavailable");
        }
    }
}
