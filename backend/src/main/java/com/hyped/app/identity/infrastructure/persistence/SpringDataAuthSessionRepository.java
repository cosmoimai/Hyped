package com.hyped.app.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {

    Optional<AuthSessionEntity> findByIdAndUserId(UUID id, UUID userId);

    List<AuthSessionEntity> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(
            UUID userId, Instant now);
}
