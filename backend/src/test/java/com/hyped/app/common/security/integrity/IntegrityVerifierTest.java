package com.hyped.app.common.security.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntegrityVerifierTest {
    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
    private final IntegrityDecoder decoder = mock(IntegrityDecoder.class);
    private final IntegrityReplayStore replay = mock(IntegrityReplayStore.class);
    private ObjectNode payload;
    private IntegrityVerifier verifier;

    @BeforeEach
    void setup() throws Exception {
        payload = new ObjectMapper().createObjectNode();
        payload.putObject("requestDetails").put("requestPackageName", "com.hyped.hyped")
                .put("requestHash", "bound").put("timestampMillis", NOW.toEpochMilli());
        var app = payload.putObject("appIntegrity").put("packageName", "com.hyped.hyped")
                .put("appRecognitionVerdict", "PLAY_RECOGNIZED");
        app.putArray("certificateSha256Digest").add("A".repeat(43));
        payload.putObject("deviceIntegrity").putArray("deviceRecognitionVerdict").add("MEETS_DEVICE_INTEGRITY");
        payload.putObject("accountDetails").put("appLicensingVerdict", "LICENSED");
        when(decoder.decode(any())).thenReturn(payload);
        when(replay.claim(any(), any(), any())).thenReturn(true);
        verifier = new IntegrityVerifier(new IntegrityProperties(IntegrityProperties.Mode.ENFORCE,
                "com.hyped.hyped", Set.of("A".repeat(43))), decoder, replay, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void validOfficialVerdictIsBoundAndConsumed() {
        assertThat(verifier.verify("opaque.evidence", "bound", NOW.toEpochMilli()))
                .isEqualTo(IntegrityVerifier.Outcome.VALID);
    }

    @Test
    void missingAndMalformedEvidenceNeverReachGoogle() {
        assertThat(verifier.verify(null, "bound", NOW.toEpochMilli())).isEqualTo(IntegrityVerifier.Outcome.MISSING);
        assertThat(verifier.verify("secret\n", "bound", NOW.toEpochMilli()))
                .isEqualTo(IntegrityVerifier.Outcome.INVALID);
        assertThat(verifier.verify("A".repeat(32769), "bound", NOW.toEpochMilli()))
                .isEqualTo(IntegrityVerifier.Outcome.INVALID);
        verifyNoInteractions(decoder);
    }

    @Test
    void staleOrFutureRequestIsRejected() {
        assertThat(verifier.verify("evidence", "bound", NOW.minusSeconds(121).toEpochMilli()))
                .isEqualTo(IntegrityVerifier.Outcome.INVALID);
        assertThat(verifier.verify("evidence", "bound", NOW.plusSeconds(11).toEpochMilli()))
                .isEqualTo(IntegrityVerifier.Outcome.INVALID);
        verifyNoInteractions(decoder);
    }

    @Test
    void differentRequestOrAccountCannotReuseEvidence() {
        assertThat(verifier.verify("evidence", "another", NOW.toEpochMilli()))
                .isEqualTo(IntegrityVerifier.Outcome.INVALID);
    }

    @Test
    void expiredGoogleVerdictIsRejected() {
        ((ObjectNode) payload.path("requestDetails")).put("timestampMillis", NOW.minusSeconds(121).toEpochMilli());
        assertThat(verifier.verify("evidence", "bound", NOW.toEpochMilli()))
                .isEqualTo(IntegrityVerifier.Outcome.INVALID);
    }

    @Test
    void packageCertificateLicenseAndDeviceAreRequired() {
        for (String field : Set.of("appIntegrity", "accountDetails", "deviceIntegrity", "requestDetails")) {
            ObjectNode saved = payload.deepCopy();
            payload.remove(field);
            assertThat(verifier.verify("evidence", "bound", NOW.toEpochMilli()))
                    .isEqualTo(IntegrityVerifier.Outcome.INVALID);
            payload.setAll(saved);
        }
        ((ObjectNode) payload.path("appIntegrity")).putArray("certificateSha256Digest").add("B".repeat(43));
        assertThat(verifier.verify("evidence", "bound", NOW.toEpochMilli()))
                .isEqualTo(IntegrityVerifier.Outcome.INVALID);
    }

    @Test
    void duplicateDeliveryIsRejectedByDurableClaim() {
        when(replay.claim(any(), any(), any())).thenReturn(true, false);
        assertThat(verifier.verify("evidence", "bound", NOW.toEpochMilli()))
                .isEqualTo(IntegrityVerifier.Outcome.VALID);
        assertThat(verifier.verify("evidence", "bound", NOW.toEpochMilli()))
                .isEqualTo(IntegrityVerifier.Outcome.REPLAYED);
    }

    @Test
    void providerFailureIsSanitized() throws Exception {
        when(decoder.decode(any())).thenThrow(new IllegalStateException("PRIVATE_TOKEN"));
        assertThat(verifier.verify("evidence", "bound", NOW.toEpochMilli()))
                .isEqualTo(IntegrityVerifier.Outcome.UNAVAILABLE);
    }

    @Test
    void enforcementCannotStartWithoutCertificateAllowlist() {
        assertThatThrownBy(() -> new IntegrityProperties(IntegrityProperties.Mode.ENFORCE, null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("PRIVATE_TOKEN");
    }
}
