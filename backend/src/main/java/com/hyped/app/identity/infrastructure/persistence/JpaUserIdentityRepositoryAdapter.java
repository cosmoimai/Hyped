package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.application.port.out.UserIdentityRepository;
import com.hyped.app.identity.domain.IdentityProvider;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.identity.domain.UserIdentity;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class JpaUserIdentityRepositoryAdapter implements UserIdentityRepository {

    private final SpringDataUserIdentityRepository repository;
    private final UserIdentityPersistenceMapper mapper;

    JpaUserIdentityRepositoryAdapter(
            SpringDataUserIdentityRepository repository, UserIdentityPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<UserIdentity> findByProviderSubject(IdentityProvider provider, byte[] providerSubjectHmac) {
        Objects.requireNonNull(provider, "provider");
        return repository.findByProviderAndProviderSubjectHmac(provider, copyHmac(providerSubjectHmac))
                .map(mapper::toDomain);
    }

    @Override
    public List<UserIdentity> findVerifiedByEmailHmac(byte[] emailHmac) {
        return repository.findByEmailHmacAndEmailVerifiedTrue(copyHmac(emailHmac))
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsByUserIdAndProvider(UserId userId, IdentityProvider provider) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(provider, "provider");
        return repository.existsByUserIdAndProvider(userId.value(), provider);
    }

    @Override
    @Transactional
    public boolean createIfProviderSubjectAbsent(UserIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return repository.createIfProviderSubjectAbsent(
                identity.id().value(), identity.userId().value(),
                identity.provider().name().toLowerCase(Locale.ROOT), identity.providerSubjectHmac(),
                identity.emailCiphertext(), identity.emailHmac(), identity.emailVerified(),
                identity.linkedAt(), identity.lastVerifiedAt(), identity.createdAt()) > 0;
    }

    @Override
    @Transactional
    public UserIdentity save(UserIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(identity)));
    }

    private static byte[] copyHmac(byte[] value) {
        Objects.requireNonNull(value, "HMAC");
        if (value.length != 32) {
            throw new IllegalArgumentException("Lookup HMAC must contain exactly 32 bytes");
        }
        return value.clone();
    }
}
