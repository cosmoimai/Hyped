package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.IdentityProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataUserIdentityRepository extends JpaRepository<UserIdentityEntity, UUID> {

    Optional<UserIdentityEntity> findByProviderAndProviderSubjectHmac(
            IdentityProvider provider, byte[] providerSubjectHmac);

    List<UserIdentityEntity> findByEmailHmacAndEmailVerifiedTrue(byte[] emailHmac);

    boolean existsByUserIdAndProvider(UUID userId, IdentityProvider provider);
}
