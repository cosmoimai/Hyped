package com.hyped.app.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataDeviceRegistrationRepository extends JpaRepository<DeviceRegistrationEntity, UUID> {

    Optional<DeviceRegistrationEntity> findByUserIdAndInstallationId(UUID userId, UUID installationId);

    Optional<DeviceRegistrationEntity> findByIdAndUserId(UUID id, UUID userId);

    List<DeviceRegistrationEntity> findByUserIdAndInvalidatedAtIsNullOrderByLastSeenAtDesc(UUID userId);

    long countByUserIdAndInvalidatedAtIsNull(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE app.device_registration
            SET invalidated_at = :invalidatedAt, updated_at = GREATEST(updated_at, :invalidatedAt)
            WHERE id = :id AND user_id = :userId AND invalidated_at IS NULL
                AND created_at <= :invalidatedAt
            """, nativeQuery = true)
    int invalidate(@Param("id") UUID id, @Param("userId") UUID userId,
            @Param("invalidatedAt") Instant invalidatedAt);
}
