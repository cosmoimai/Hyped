package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Component 
class JpaUserAccountRepositoryAdapter implements UserAccountRepository {

    private final SpringDataUserAccountRepository repository;
    private final UserAccountPersistenceMapper mapper;

    JpaUserAccountRepositoryAdapter(
            SpringDataUserAccountRepository repository, UserAccountPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findById(UserId userId) {
        Objects.requireNonNull(userId, "userId");
        return repository.findById(userId.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UserAccount> findByIdForUpdate(UserId userId) {
        Objects.requireNonNull(userId, "userId");
        return repository.findByIdForUpdate(userId.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public UserAccount save(UserAccount account) {
        Objects.requireNonNull(account, "account");
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(account)));
    }
}
