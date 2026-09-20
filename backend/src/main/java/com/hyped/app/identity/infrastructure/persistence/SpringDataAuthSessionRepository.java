package com.hyped.app.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {

    @Query(value = "SELECT * FROM app.auth_session WHERE id = :id AND user_id = :userId FOR UPDATE", nativeQuery = true)
    Optional<AuthSessionEntity> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    Optional<AuthSessionEntity> findByIdAndUserId(UUID id, UUID userId);

    List<AuthSessionEntity> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(
            UUID userId, Instant now);
}
