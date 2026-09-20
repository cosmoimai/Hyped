package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.AccountStatus;
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
@Table(name = "app_user", schema = "app")
@Access(AccessType.FIELD)
class UserAccountEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Convert(converter = AccountStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "failed_auth_count", nullable = false)
    private short failedAuthCount;

    @Column(name = "pii_key_reference", length = 255)
    private String piiKeyReference;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Column(name = "pii_destroyed_at")
    private Instant piiDestroyedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccountEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public short getFailedAuthCount() {
        return failedAuthCount;
    }

    public void setFailedAuthCount(short failedAuthCount) {
        this.failedAuthCount = failedAuthCount;
    }

    public String getPiiKeyReference() {
        return piiKeyReference;
    }

    public void setPiiKeyReference(String piiKeyReference) {
        this.piiKeyReference = piiKeyReference;
    }

    public Instant getDeletionRequestedAt() {
        return deletionRequestedAt;
    }

    public void setDeletionRequestedAt(Instant deletionRequestedAt) {
        this.deletionRequestedAt = deletionRequestedAt;
    }

    public Instant getPiiDestroyedAt() {
        return piiDestroyedAt;
    }

    public void setPiiDestroyedAt(Instant piiDestroyedAt) {
        this.piiDestroyedAt = piiDestroyedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
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
