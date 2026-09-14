package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.RefreshTokenState;
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
@Table(name = "refresh_token_record", schema = "app")
@Access(AccessType.FIELD)
class RefreshTokenRecordEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "token_digest", nullable = false)
    private byte[] tokenDigest;

    @Convert(converter = RefreshTokenStateConverter.class)
    @Column(name = "state", nullable = false, length = 16)
    private RefreshTokenState state;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "consumed_at", nullable = true)
    private Instant consumedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "replaced_by_id", nullable = true)
    private UUID replacedById;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshTokenRecordEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public byte[] getTokenDigest() {
        return tokenDigest == null ? null : tokenDigest.clone();
    }

    public void setTokenDigest(byte[] tokenDigest) {
        this.tokenDigest = tokenDigest == null ? null : tokenDigest.clone();
    }

    public RefreshTokenState getState() {
        return state;
    }

    public void setState(RefreshTokenState state) {
        this.state = state;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public UUID getReplacedById() {
        return replacedById;
    }

    public void setReplacedById(UUID replacedById) {
        this.replacedById = replacedById;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
