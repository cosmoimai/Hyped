package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.application.port.out.RefreshTokenRecordRepository;
import com.hyped.app.identity.domain.RefreshTokenRecord;
import com.hyped.app.identity.domain.SessionId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class JpaRefreshTokenRecordRepositoryAdapter implements RefreshTokenRecordRepository {

    private final SpringDataRefreshTokenRecordRepository repository;
    private final RefreshTokenRecordPersistenceMapper mapper;

    JpaRefreshTokenRecordRepositoryAdapter(
            SpringDataRefreshTokenRecordRepository repository, RefreshTokenRecordPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public RefreshTokenRecord save(RefreshTokenRecord token) {
        Objects.requireNonNull(token, "token");
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(token)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RefreshTokenRecord> findByTokenDigestForUpdate(byte[] tokenDigest) {
        Objects.requireNonNull(tokenDigest, "tokenDigest");
        if (tokenDigest.length != 32) {
            throw new IllegalArgumentException("tokenDigest must contain exactly 32 bytes");
        }
        return repository.findByTokenDigestForUpdate(tokenDigest.clone()).map(mapper::toDomain);
    }

    @Override
    public List<RefreshTokenRecord> findBySessionId(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return repository.findBySessionIdOrderByIssuedAtAscIdAsc(sessionId.value())
                .stream().map(mapper::toDomain).toList();
    }
}
