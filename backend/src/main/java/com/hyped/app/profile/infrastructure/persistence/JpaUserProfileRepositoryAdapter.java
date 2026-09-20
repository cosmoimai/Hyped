package com.hyped.app.profile.infrastructure.persistence;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.profile.application.port.out.UserProfileRepository;
import com.hyped.app.profile.domain.UserProfile;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class JpaUserProfileRepositoryAdapter implements UserProfileRepository {

    private final SpringDataUserProfileRepository repository;
    private final UserProfilePersistenceMapper mapper;
    private final EntityManager entityManager;

    JpaUserProfileRepositoryAdapter(
            SpringDataUserProfileRepository repository,
            UserProfilePersistenceMapper mapper,
            EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<UserProfile> findByUserId(UserId userId) {
        Objects.requireNonNull(userId, "userId");
        return repository.findById(userId.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public UserProfile save(UserProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (profile.profileRevision() != 1) {
            throw new IllegalArgumentException("A new profile must have revision 1");
        }
        UserProfileEntity entity = mapper.toEntity(profile);
        entityManager.persist(entity);
        entityManager.flush();
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional
    public Optional<UserProfile> update(UserProfile profile, long expectedProfileRevision) {
        Objects.requireNonNull(profile, "profile");
        if (expectedProfileRevision <= 0 || profile.profileRevision() != expectedProfileRevision) {
            throw new IllegalArgumentException("Expected revision must match the positive profile revision");
        }
        int updated = repository.updateIfRevisionMatches(
                profile.userId().value(), expectedProfileRevision, profile.displayNameCiphertext(),
                profile.providerPhotoUrlCiphertext(), profile.photoMediaId(), profile.updatedAt());
        if (updated == 0) {
            return Optional.empty();
        }
        return repository.findById(profile.userId().value()).map(mapper::toDomain);
    }
}
