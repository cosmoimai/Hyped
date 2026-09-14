package com.hyped.app.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class UserAccountRepositoryIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-14T10:00:00.123456Z");
    private static final Instant UPDATED_AT = CREATED_AT.plusSeconds(60);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesAndLoadsActiveAccountWithLowercaseDatabaseStatus() {
        UserAccount account = account(AccountStatus.ACTIVE);

        UserAccount saved = repository.save(account);
        entityManager.clear();

        assertThat(saved).isEqualTo(account);
        assertThat(repository.findById(account.id())).contains(account);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM app.app_user WHERE id = ?", String.class, account.id().value()))
                .isEqualTo("active");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT success FROM flyway.flyway_schema_history WHERE version = '2'
                """, Boolean.class)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(AccountStatus.class)
    void domainValuesSurviveRoundTrip(AccountStatus status) {
        UserAccount account = account(status);

        repository.save(account);
        entityManager.clear();

        assertThat(repository.findById(account.id())).contains(account);
    }

    @Test
    void findsAccountForUpdateInsideTransaction() {
        UserAccount account = account(AccountStatus.ACTIVE);
        repository.save(account);
        entityManager.clear();

        assertThat(repository.findByIdForUpdate(account.id())).contains(account);
    }

    @Test
    void updatesAuthenticationFields() {
        UserAccount account = account(AccountStatus.ACTIVE);
        repository.save(account);
        entityManager.clear();
        UserAccount loaded = repository.findByIdForUpdate(account.id()).orElseThrow();
        Instant failureAt = UPDATED_AT.plusSeconds(30);
        UserAccount locked = loaded.recordAttributableFailure(failureAt, 5, Duration.ofMinutes(15));

        UserAccount saved = repository.save(locked);
        entityManager.clear();

        assertThat(saved).isEqualTo(locked);
        UserAccount reloaded = repository.findById(account.id()).orElseThrow();
        assertThat(reloaded).isEqualTo(locked);
        assertThat(reloaded.failedAuthCount()).isEqualTo(5);
        assertThat(reloaded.status()).isEqualTo(AccountStatus.LOCKED);
        assertThat(reloaded.lockedUntil()).isEqualTo(failureAt.plus(Duration.ofMinutes(15)));
        assertThat(reloaded.updatedAt()).isEqualTo(failureAt);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM app.app_user WHERE id = ?", String.class, account.id().value()))
                .isEqualTo("locked");
    }

    @Test
    void missingAccountReturnsEmpty() {
        UserId missingId = new UserId(UUID.randomUUID());

        assertThat(repository.findById(missingId)).isEmpty();
        assertThat(repository.findByIdForUpdate(missingId)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lockingReadRequiresAnExistingTransaction() {
        assertThatThrownBy(() -> repository.findByIdForUpdate(new UserId(UUID.randomUUID())))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private static UserAccount account(AccountStatus status) {
        boolean deleting = status == AccountStatus.DELETION_PENDING || status == AccountStatus.DELETED;
        return new UserAccount(new UserId(UUID.randomUUID()), status,
                status == AccountStatus.LOCKED ? UPDATED_AT.plusSeconds(900) : null,
                4, "external-key-reference",
                deleting ? CREATED_AT.plusSeconds(10) : null,
                status == AccountStatus.DELETED ? CREATED_AT.plusSeconds(20) : null,
                status == AccountStatus.DELETED ? CREATED_AT.plusSeconds(30) : null,
                CREATED_AT, UPDATED_AT);
    }
}
