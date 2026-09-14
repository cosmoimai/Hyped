package com.hyped.app.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeviceRegistrationTest {

    private static final Instant CREATED = Instant.parse("2026-09-15T10:00:00Z");
    private static final DeviceId ID = new DeviceId(UUID.randomUUID());
    private static final UserId USER = new UserId(UUID.randomUUID());
    private static final InstallationId INSTALLATION = new InstallationId(UUID.randomUUID());

    @Test
    void trimsNamesAndSupportsRegistrationWithoutFcm() {
        DeviceRegistration device = device("  Pixel  ", null, null, false, CREATED, null, CREATED);

        assertThat(device.deviceName()).isEqualTo("Pixel");
        assertThat(device.isActive()).isTrue();
        assertThat(device.fcmTokenCiphertext()).isNull();
        assertThat(device.fcmTokenFingerprint()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void rejectsBlankNames(String name) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> device(name, null, null, false, CREATED, null, CREATED));
    }

    @Test
    void boundsNamesByUnicodeCharacterCount() {
        String name = "😀".repeat(80);
        assertThat(device(name, null, null, false, CREATED, null, CREATED).deviceName()).isEqualTo(name);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> device(name + "x", null, null, false, CREATED, null, CREATED));
    }

    @Test
    void requiresFcmPairAndFcmValuesForNotifications() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> device("Phone", new byte[1], null, false, CREATED, null, CREATED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> device("Phone", null, new byte[32], false, CREATED, null, CREATED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> device("Phone", null, null, true, CREATED, null, CREATED));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 31, 33})
    void requiresThirtyTwoByteFingerprint(int length) {
        assertThatIllegalArgumentException().isThrownBy(() -> device(
                "Phone", new byte[1], new byte[length], true, CREATED, null, CREATED));
    }

    @Test
    void rejectsTimestampsBeforeCreation() {
        Instant before = CREATED.minusNanos(1);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> device("Phone", null, null, false, before, null, CREATED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> device("Phone", null, null, false, CREATED, before, CREATED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> device("Phone", null, null, false, CREATED, null, before));
        assertThat(device("Phone", null, null, false, CREATED, CREATED, CREATED).isActive()).isFalse();
    }

    @Test
    void copiesArraysAndOmitsFcmValuesFromStringRepresentation() {
        byte[] ciphertext = {11, 12, 13};
        byte[] fingerprint = new byte[32];
        DeviceRegistration device = device("Phone", ciphertext, fingerprint, true, CREATED, null, CREATED);
        DeviceRegistration equal = device("Phone", ciphertext, fingerprint, true, CREATED, null, CREATED);
        int hash = device.hashCode();
        ciphertext[0] = 99;
        fingerprint[0] = 99;
        device.fcmTokenCiphertext()[0] = 88;
        device.fcmTokenFingerprint()[0] = 88;

        assertThat(device.fcmTokenCiphertext()).containsExactly(11, 12, 13);
        assertThat(device.fcmTokenFingerprint()).containsExactly(new byte[32]);
        assertThat(device).isEqualTo(equal).hasSameHashCodeAs(equal);
        assertThat(device.hashCode()).isEqualTo(hash);
        assertThat(device.toString()).isEqualTo("DeviceRegistration[id=" + ID + ", userId=" + USER
                + ", platform=ANDROID, active=true]");
    }

    private static DeviceRegistration device(String name, byte[] ciphertext, byte[] fingerprint,
            boolean notifications, Instant lastSeenAt, Instant invalidatedAt, Instant updatedAt) {
        return new DeviceRegistration(ID, USER, DevicePlatform.ANDROID, INSTALLATION, name, ciphertext, fingerprint,
                notifications, lastSeenAt, invalidatedAt, CREATED, updatedAt);
    }
}
