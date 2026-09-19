package com.hyped.app.profile.infrastructure.persistence;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profile", schema = "app")
@Access(AccessType.FIELD)
class UserProfileEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "display_name_ciphertext", nullable = false)
    private byte[] displayNameCiphertext;

    @Column(name = "provider_photo_url_ciphertext")
    private byte[] providerPhotoUrlCiphertext;

    @Column(name = "photo_media_id")
    private UUID photoMediaId;

    @Column(name = "profile_revision", nullable = false)
    private long profileRevision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfileEntity() {
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public byte[] getDisplayNameCiphertext() {
        return copy(displayNameCiphertext);
    }

    public void setDisplayNameCiphertext(byte[] displayNameCiphertext) {
        this.displayNameCiphertext = copy(displayNameCiphertext);
    }

    public byte[] getProviderPhotoUrlCiphertext() {
        return copy(providerPhotoUrlCiphertext);
    }

    public void setProviderPhotoUrlCiphertext(byte[] providerPhotoUrlCiphertext) {
        this.providerPhotoUrlCiphertext = copy(providerPhotoUrlCiphertext);
    }

    public UUID getPhotoMediaId() {
        return photoMediaId;
    }

    public void setPhotoMediaId(UUID photoMediaId) {
        this.photoMediaId = photoMediaId;
    }

    public long getProfileRevision() {
        return profileRevision;
    }

    public void setProfileRevision(long profileRevision) {
        this.profileRevision = profileRevision;
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

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }
}
