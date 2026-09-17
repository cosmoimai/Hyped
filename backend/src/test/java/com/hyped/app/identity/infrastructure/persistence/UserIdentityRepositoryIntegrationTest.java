package com.hyped.app.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.application.port.out.UserIdentityRepository;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.IdentityId;
import com.hyped.app.identity.domain.IdentityProvider;
import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.identity.domain.UserIdentity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class UserIdentityRepositoryIntegrationTest {

    private static final Instant CREATED = Instant.parse("2026-09-17T10:00:00.123456Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private UserIdentityRepository identities;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private UserIdentityPersistenceMapper mapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @CsvSource({"GOOGLE, google", "APPLE, apple"})
    void savesAndLoadsIdentityWithLowercaseProvider(IdentityProvider provider, String storedProvider) {
        UserIdentity identity = identity(user(), provider, hmac(1), new byte[] {2, 3}, hmac(4), true);

        assertThat(identities.save(identity)).isEqualTo(identity);
        entityManager.clear();

        assertThat(identities.findByProviderSubject(provider, hmac(1))).contains(identity);
        assertThat(jdbcTemplate.queryForObject("SELECT provider FROM app.user_identity WHERE id = ?",
                String.class, identity.id().value())).isEqualTo(storedProvider);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT success FROM flyway.flyway_schema_history WHERE version = '2'", Boolean.class)).isTrue();
    }

    @Test
    void identityWithoutEmailCanBePersisted() {
        UserIdentity identity = identities.save(identity(user(), IdentityProvider.APPLE, hmac(1), null, null, false));
        entityManager.clear();

        UserIdentity loaded = identities.findByProviderSubject(IdentityProvider.APPLE, hmac(1)).orElseThrow();
        assertThat(loaded).isEqualTo(identity);
        assertThat(loaded.emailCiphertext()).isNull();
        assertThat(loaded.emailHmac()).isNull();
        assertThat(loaded.emailVerified()).isFalse();
    }

    @Test
    void providerLookupUsesBothProviderAndSubject() {
        UserId owner = user();
        UserIdentity google = identities.save(identity(owner, IdentityProvider.GOOGLE, hmac(1), null, null, false));
        assertThat(identities.findByProviderSubject(IdentityProvider.APPLE, hmac(1))).isEmpty();
        UserIdentity apple = identities.save(identity(owner, IdentityProvider.APPLE, hmac(1), null, null, false));
        entityManager.clear();

        assertThat(identities.findByProviderSubject(IdentityProvider.GOOGLE, hmac(1))).contains(google);
        assertThat(identities.findByProviderSubject(IdentityProvider.APPLE, hmac(1))).contains(apple);
        assertThat(identities.findByProviderSubject(IdentityProvider.GOOGLE, hmac(2))).isEmpty();
    }

    @Test
    void verifiedEmailLookupReturnsMultipleCandidatesWithoutMergingAccounts() {
        UserId firstUser = user();
        UserId secondUser = user();
        UserIdentity first = identities.save(
                identity(firstUser, IdentityProvider.GOOGLE, hmac(1), new byte[] {1}, hmac(10), true));
        UserIdentity second = identities.save(
                identity(secondUser, IdentityProvider.APPLE, hmac(2), new byte[] {2}, hmac(10), true));
        UserIdentity unverified = identities.save(
                identity(user(), IdentityProvider.GOOGLE, hmac(3), new byte[] {3}, hmac(10), false));
        identities.save(identity(user(), IdentityProvider.GOOGLE, hmac(4), new byte[] {4}, hmac(20), true));
        entityManager.clear();

        assertThat(identities.findVerifiedByEmailHmac(hmac(10))).containsExactlyInAnyOrder(first, second);
        assertThat(identities.findVerifiedByEmailHmac(hmac(99))).isEmpty();
        assertThat(identities.findByProviderSubject(unverified.provider(), unverified.providerSubjectHmac()))
                .contains(unverified);
        assertThat(accounts.findById(firstUser)).isPresent();
        assertThat(accounts.findById(secondUser)).isPresent();
    }

    @Test
    void unverifiedEmailDoesNotProduceAnyCandidates() {
        identities.save(identity(user(), IdentityProvider.GOOGLE, hmac(1), new byte[] {1}, hmac(10), false));
        entityManager.clear();
        assertThat(identities.findVerifiedByEmailHmac(hmac(10))).isEmpty();
    }

    @Test
    void duplicateProviderSubjectIsRejectedAcrossUsers() {
        identities.save(identity(user(), IdentityProvider.GOOGLE, hmac(1), null, null, false));
        UserIdentity duplicate = identity(user(), IdentityProvider.GOOGLE, hmac(1), null, null, false);

        assertThatThrownBy(() -> identities.save(duplicate)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("user_identity_provider_subject_unique");
    }

    @Test
    void sameUserCannotHaveTwoIdentitiesForOneProvider() {
        UserId owner = user();
        identities.save(identity(owner, IdentityProvider.APPLE, hmac(1), null, null, false));
        UserIdentity duplicate = identity(owner, IdentityProvider.APPLE, hmac(2), null, null, false);

        assertThatThrownBy(() -> identities.save(duplicate)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("user_identity_user_provider_unique");
    }

    @Test
    void existenceCheckIsScopedByUserAndProvider() {
        UserId owner = user();
        identities.save(identity(owner, IdentityProvider.GOOGLE, hmac(1), null, null, false));

        assertThat(identities.existsByUserIdAndProvider(owner, IdentityProvider.GOOGLE)).isTrue();
        assertThat(identities.existsByUserIdAndProvider(owner, IdentityProvider.APPLE)).isFalse();
        assertThat(identities.existsByUserIdAndProvider(user(), IdentityProvider.GOOGLE)).isFalse();
    }

    @Test
    void protectedArraysRemainIndependentAcrossAllPersistenceBoundaries() {
        byte[] subject = hmac(1);
        byte[] ciphertext = {2, 3};
        byte[] email = hmac(4);
        UserIdentity original = identity(user(), IdentityProvider.GOOGLE, subject, ciphertext, email, true);
        subject[0] = 99;
        ciphertext[0] = 99;
        email[0] = 99;
        UserIdentityEntity entity = mapper.toEntity(original);
        byte[] entitySubject = hmac(1);
        byte[] entityCiphertext = {2, 3};
        byte[] entityEmail = hmac(4);
        entity.setProviderSubjectHmac(entitySubject);
        entity.setEmailCiphertext(entityCiphertext);
        entity.setEmailHmac(entityEmail);
        entitySubject[0] = 88;
        entityCiphertext[0] = 88;
        entityEmail[0] = 88;
        entity.getProviderSubjectHmac()[0] = 77;
        entity.getEmailCiphertext()[0] = 77;
        entity.getEmailHmac()[0] = 77;
        UserIdentity mapped = mapper.toDomain(entity);
        entity.setProviderSubjectHmac(hmac(8));
        entity.setEmailCiphertext(new byte[] {8});
        entity.setEmailHmac(hmac(8));
        assertThat(mapped).isEqualTo(original);
        UserIdentity saved = identities.save(mapped);
        saved.providerSubjectHmac()[0] = 66;
        saved.emailCiphertext()[0] = 66;
        saved.emailHmac()[0] = 66;
        entityManager.flush();
        entityManager.clear();

        byte[] lookup = hmac(1);
        UserIdentity loaded = identities.findByProviderSubject(IdentityProvider.GOOGLE, lookup).orElseThrow();
        lookup[0] = 55;
        loaded.providerSubjectHmac()[0] = 55;
        loaded.emailCiphertext()[0] = 55;
        loaded.emailHmac()[0] = 55;
        assertThat(loaded).isEqualTo(original);
        entityManager.flush();
        entityManager.clear();
        assertThat(identities.findByProviderSubject(IdentityProvider.GOOGLE, hmac(1))).contains(original);
        UserIdentity candidate = identities.findVerifiedByEmailHmac(hmac(4)).getFirst();
        candidate.emailHmac()[0] = 44;
        assertThat(candidate).isEqualTo(original);
    }

    private UserId user() {
        UserId id = new UserId(UUID.randomUUID());
        accounts.save(new UserAccount(id, AccountStatus.ACTIVE, null, 0, null, null, null, null, CREATED, CREATED));
        return id;
    }

    private static UserIdentity identity(UserId owner, IdentityProvider provider, byte[] subject,
            byte[] ciphertext, byte[] email, boolean verified) {
        return new UserIdentity(new IdentityId(UUID.randomUUID()), owner, provider,
                subject, ciphertext, email, verified,
                CREATED.plusSeconds(1), CREATED.plusSeconds(2), CREATED);
    }

    private static byte[] hmac(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
