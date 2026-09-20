package com.hyped.app.profile.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataUserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE app.user_profile
            SET display_name_ciphertext = :displayNameCiphertext,
                provider_photo_url_ciphertext = :providerPhotoUrlCiphertext,
                photo_media_id = :photoMediaId,
                profile_revision = profile_revision + 1,
                updated_at = :updatedAt
            WHERE user_id = :userId AND profile_revision = :expectedProfileRevision
            """, nativeQuery = true)
    int updateIfRevisionMatches(
            @Param("userId") UUID userId,
            @Param("expectedProfileRevision") long expectedProfileRevision,
            @Param("displayNameCiphertext") byte[] displayNameCiphertext,
            @Param("providerPhotoUrlCiphertext") byte[] providerPhotoUrlCiphertext,
            @Param("photoMediaId") UUID photoMediaId,
            @Param("updatedAt") Instant updatedAt);
}
