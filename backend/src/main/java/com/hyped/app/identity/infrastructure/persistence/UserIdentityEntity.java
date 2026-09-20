package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.IdentityProvider;
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
@Table(name = "user_identity", schema = "app")
@Access(AccessType.FIELD)
class UserIdentityEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Convert(converter = IdentityProviderConverter.class)
    @Column(name = "provider", nullable = false, length = 16)
    private IdentityProvider provider;

    @Column(name = "provider_subject_hmac", nullable = false)
    private byte[] providerSubjectHmac;

    @Column(name = "email_ciphertext", nullable = true)
    private byte[] emailCiphertext;

    @Column(name = "email_hmac", nullable = true)
    private byte[] emailHmac;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Column(name = "last_verified_at", nullable = false)
    private Instant lastVerifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserIdentityEntity() {}

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

    public IdentityProvider getProvider() {
        return provider;
    }

    public void setProvider(IdentityProvider provider) {
        this.provider = provider;
    }

    public byte[] getProviderSubjectHmac() {
        return providerSubjectHmac == null ? null : providerSubjectHmac.clone();
    }

    public void setProviderSubjectHmac(byte[] providerSubjectHmac) {
        this.providerSubjectHmac = providerSubjectHmac == null ? null : providerSubjectHmac.clone();
    }

    public byte[] getEmailCiphertext() {
        return emailCiphertext == null ? null : emailCiphertext.clone();
    }

    public void setEmailCiphertext(byte[] emailCiphertext) {
        this.emailCiphertext = emailCiphertext == null ? null : emailCiphertext.clone();
    }

    public byte[] getEmailHmac() {
        return emailHmac == null ? null : emailHmac.clone();
    }

    public void setEmailHmac(byte[] emailHmac) {
        this.emailHmac = emailHmac == null ? null : emailHmac.clone();
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(Instant linkedAt) {
        this.linkedAt = linkedAt;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(Instant lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
