package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.domain.AuthSession;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class JpaAuthSessionRepositoryAdapter implements AuthSessionRepository {

    private final SpringDataAuthSessionRepository repository;
    private final AuthSessionPersistenceMapper mapper;

    JpaAuthSessionRepositoryAdapter(SpringDataAuthSessionRepository repository, AuthSessionPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<AuthSession> findByIdAndUserId(SessionId sessionId, UserId userId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(userId, "userId");
        return repository.findByIdAndUserId(sessionId.value(), userId.value()).map(mapper::toDomain);
    }

    @Override
    public List<AuthSession> findActiveByUserId(UserId userId, Instant now) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(now, "now");
        return repository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(userId.value(), now)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public AuthSession save(AuthSession session) {
        Objects.requireNonNull(session, "session");
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(session)));
    }
}
