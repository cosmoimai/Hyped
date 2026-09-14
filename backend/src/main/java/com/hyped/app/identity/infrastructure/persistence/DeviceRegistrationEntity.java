package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.DevicePlatform;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_registration", schema = "app")
@Access(AccessType.FIELD)
class DeviceRegistrationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Convert(converter = DevicePlatformConverter.class)
    @Column(name = "platform", nullable = false, length = 16)
    private DevicePlatform platform;

    @Column(name = "installation_id", nullable = false)
    private UUID installationId;

    @Column(name = "device_name", nullable = false, length = 80)
    private String deviceName;

    @Column(name = "fcm_token_ciphertext", nullable = true)
    private byte[] fcmTokenCiphertext;

    @Column(name = "fcm_token_fingerprint", nullable = true)
    private byte[] fcmTokenFingerprint;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "invalidated_at", nullable = true)
    private Instant invalidatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeviceRegistrationEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public void setPlatform(DevicePlatform platform) {
        this.platform = platform;
    }

    public UUID getInstallationId() {
        return installationId;
    }

    public void setInstallationId(UUID installationId) {
        this.installationId = installationId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public byte[] getFcmTokenCiphertext() {
        return fcmTokenCiphertext == null ? null : fcmTokenCiphertext.clone();
    }

    public void setFcmTokenCiphertext(byte[] fcmTokenCiphertext) {
        this.fcmTokenCiphertext = fcmTokenCiphertext == null ? null : fcmTokenCiphertext.clone();
    }

    public byte[] getFcmTokenFingerprint() {
        return fcmTokenFingerprint == null ? null : fcmTokenFingerprint.clone();
    }

    public void setFcmTokenFingerprint(byte[] fcmTokenFingerprint) {
        this.fcmTokenFingerprint = fcmTokenFingerprint == null ? null : fcmTokenFingerprint.clone();
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    public void setInvalidatedAt(Instant invalidatedAt) {
        this.invalidatedAt = invalidatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
