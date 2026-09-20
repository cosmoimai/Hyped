package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.IdentityProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataUserIdentityRepository extends JpaRepository<UserIdentityEntity, UUID> {

    Optional<UserIdentityEntity> findByProviderAndProviderSubjectHmac(
            IdentityProvider provider, byte[] providerSubjectHmac);

    List<UserIdentityEntity> findByEmailHmacAndEmailVerifiedTrue(byte[] emailHmac);

    boolean existsByUserIdAndProvider(UUID userId, IdentityProvider provider);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO app.user_identity
                (id, user_id, provider, provider_subject_hmac, email_ciphertext, email_hmac,
                 email_verified, linked_at, last_verified_at, created_at)
            VALUES
                (:id, :userId, :provider, :providerSubjectHmac, :emailCiphertext, :emailHmac,
                 :emailVerified, :linkedAt, :lastVerifiedAt, :createdAt)
            ON CONFLICT (provider, provider_subject_hmac) DO NOTHING
            """, nativeQuery = true)
    int createIfProviderSubjectAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("provider") String provider,
            @Param("providerSubjectHmac") byte[] providerSubjectHmac,
            @Param("emailCiphertext") byte[] emailCiphertext,
            @Param("emailHmac") byte[] emailHmac,
            @Param("emailVerified") boolean emailVerified,
            @Param("linkedAt") java.time.Instant linkedAt,
            @Param("lastVerifiedAt") java.time.Instant lastVerifiedAt,
            @Param("createdAt") java.time.Instant createdAt);
}
