package com.hyped.app.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.application.model.AuthenticationExchangeCommand;
import com.hyped.app.identity.application.model.AuthenticationExchangeResult;
import com.hyped.app.identity.application.model.GeneratedRefreshToken;
import com.hyped.app.identity.application.model.IssuedAccessToken;
import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.application.model.ProvisionedPersonalDataKey;
import com.hyped.app.identity.application.model.VerifiedProviderIdentity;
import com.hyped.app.identity.application.port.out.AccessTokenIssuer;
import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.application.port.out.IdGenerator;
import com.hyped.app.identity.application.port.out.IdentityLookupProtector;
import com.hyped.app.identity.application.port.out.IdentityTokenVerifier;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.application.port.out.PersonalDataKeyManager;
import com.hyped.app.identity.application.port.out.RefreshTokenDigester;
import com.hyped.app.identity.application.port.out.RefreshTokenGenerator;
import com.hyped.app.identity.application.port.out.RefreshTokenRecordRepository;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.application.port.out.UserIdentityRepository;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.DevicePlatform;
import com.hyped.app.identity.domain.IdentityProvider;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.identity.infrastructure.crypto.HmacSha256IdentityLookupProtector;
import com.hyped.app.profile.application.port.out.UserProfileRepository;
import com.hyped.app.profile.domain.UserProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Import(AuthenticationExchangeServiceIntegrationTest.TestConfig.class)
class AuthenticationExchangeServiceIntegrationTest {
    private static final Instant INITIAL_TIME = Instant.parse("2026-09-19T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private AuthenticationExchangeService exchange;

    @Autowired
    private FakeExternalProviders external;

    @Autowired
    private FailingProfileRepository profiles;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM app.refresh_token_record");
        jdbc.update("DELETE FROM app.auth_session");
        jdbc.update("DELETE FROM app.device_registration");
        jdbc.update("DELETE FROM app.user_identity");
        jdbc.update("DELETE FROM app.user_profile");
        jdbc.update("DELETE FROM app.app_user");
        external.reset();
        profiles.failNextSave = false;
        clock.set(INITIAL_TIME);
    }

    @Test
    void createsEncryptedAccountProfileIdentityAndSession() {
        external.identities.put("new-token", verified(
                IdentityProvider.GOOGLE, "google-subject", "person@example.com", true, "  Person Name  "));

        AuthenticationExchangeResult result = exchange.exchange(command("new-token", installation(1)));

        AuthenticationExchangeResult.Success success = (AuthenticationExchangeResult.Success) result;
        assertThat(success.newAccount()).isTrue();
        assertThat(count("app.app_user")).isEqualTo(1);
        assertThat(count("app.user_profile")).isEqualTo(1);
        assertThat(count("app.user_identity")).isEqualTo(1);
        assertThat(count("app.auth_session")).isEqualTo(1);
        assertThat(count("app.refresh_token_record")).isEqualTo(1);
        Map<String, Object> stored = jdbc.queryForMap("""
                SELECT u.pii_key_reference, p.display_name_ciphertext, i.email_ciphertext,
                       i.provider_subject_hmac
                FROM app.app_user u
                JOIN app.user_profile p ON p.user_id = u.id
                JOIN app.user_identity i ON i.user_id = u.id
                """);
        assertThat(stored.get("pii_key_reference")).isEqualTo(external.references.get(success.userId()));
        assertThat((byte[]) stored.get("display_name_ciphertext"))
                .isNotEqualTo("Person Name".getBytes(StandardCharsets.UTF_8));
        assertThat((byte[]) stored.get("email_ciphertext"))
                .isNotEqualTo("person@example.com".getBytes(StandardCharsets.UTF_8));
        assertThat(stored.get("provider_subject_hmac")).isInstanceOf(byte[].class);
        assertThat(external.callInsideTransaction).isFalse();
    }

    @Test
    void existingIdentityCreatesSessionAndUpdatesVerificationOnlyAfterSuccess() {
        external.identities.put("existing", verified(
                IdentityProvider.APPLE, "apple-subject", null, false, null));
        AuthenticationExchangeResult.Success first = success(
                exchange.exchange(command("existing", installation(1))));
        Instant firstVerified = lastVerified(first.userId());
        int provisions = external.provisionCalls.get();
        clock.advance(Duration.ofHours(1));

        AuthenticationExchangeResult.Success second = success(
                exchange.exchange(command("existing", installation(1))));

        assertThat(second.newAccount()).isFalse();
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(external.provisionCalls).hasValue(provisions);
        assertThat(lastVerified(first.userId())).isEqualTo(firstVerified.plus(Duration.ofHours(1)));
        assertThat(count("app.app_user")).isEqualTo(1);
        assertThat(count("app.user_identity")).isEqualTo(1);
    }

    @Test
    void verifiedEmailCandidateRequiresExplicitLinkingWithoutCreatingRowsOrKeys() {
        external.identities.put("first", verified(
                IdentityProvider.GOOGLE, "first-subject", "shared@example.com", true, "First"));
        exchange.exchange(command("first", installation(1)));
        int provisions = external.provisionCalls.get();
        external.identities.put("candidate", verified(
                IdentityProvider.APPLE, "second-subject", " SHARED@example.com ", true, "Second"));

        assertThat(exchange.exchange(command("candidate", installation(2))))
                .isInstanceOf(AuthenticationExchangeResult.AccountLinkConfirmationRequired.class);
        assertThat(external.provisionCalls).hasValue(provisions);
        assertThat(count("app.app_user")).isEqualTo(1);
        assertThat(count("app.user_identity")).isEqualTo(1);
        assertThat(count("app.user_profile")).isEqualTo(1);
    }

    @Test
    void permitsFiveDevicesAndRejectsSixthWithoutUpdatingVerificationTime() {
        external.identities.put("devices", verified(
                IdentityProvider.GOOGLE, "device-subject", null, false, "Devices"));
        AuthenticationExchangeResult.Success first = success(
                exchange.exchange(command("devices", installation(1))));
        for (int index = 2; index <= 5; index++) {
            assertThat(exchange.exchange(command("devices", installation(index))))
                    .isInstanceOf(AuthenticationExchangeResult.Success.class);
        }
        Instant beforeFailure = lastVerified(first.userId());
        clock.advance(Duration.ofMinutes(1));

        AuthenticationExchangeResult result = exchange.exchange(command("devices", installation(6)));

        AuthenticationExchangeResult.DeviceLimitReached limit =
                (AuthenticationExchangeResult.DeviceLimitReached) result;
        assertThat(limit.devices()).hasSize(5);
        assertThat(lastVerified(first.userId())).isEqualTo(beforeFailure);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM app.device_registration WHERE invalidated_at IS NULL", Integer.class))
                .isEqualTo(5);
    }

    @Test
    void blockedAccountCannotExchangeOrUpdateIdentity() {
        external.identities.put("blocked", verified(
                IdentityProvider.APPLE, "blocked-subject", null, false, "Blocked"));
        AuthenticationExchangeResult.Success created = success(
                exchange.exchange(command("blocked", installation(1))));
        Instant before = lastVerified(created.userId());
        jdbc.update("UPDATE app.app_user SET status = 'compromised' WHERE id = ?", created.userId().value());
        clock.advance(Duration.ofMinutes(10));

        AuthenticationExchangeResult.AccountUnavailable unavailable =
                (AuthenticationExchangeResult.AccountUnavailable) exchange.exchange(
                        command("blocked", installation(2)));
        assertThat(unavailable.status()).isEqualTo(AccountStatus.COMPROMISED);
        assertThat(lastVerified(created.userId())).isEqualTo(before);
        assertThat(count("app.auth_session")).isEqualTo(1);
    }

    @Test
    void concurrentFirstSignInsResolveToOneAccount() throws Exception {
        external.identities.put("race", verified(
                IdentityProvider.GOOGLE, "race-subject", null, false, "Racer"));
        external.provisionBarrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> exchange.exchange(command("race", installation(1))));
            var second = executor.submit(() -> exchange.exchange(command("race", installation(1))));
            AuthenticationExchangeResult.Success firstResult = success(first.get());
            AuthenticationExchangeResult.Success secondResult = success(second.get());
            assertThat(firstResult.userId()).isEqualTo(secondResult.userId());
            assertThat(firstResult.newAccount() || secondResult.newAccount()).isTrue();
            assertThat(firstResult.newAccount() && secondResult.newAccount()).isFalse();
        }
        assertThat(count("app.app_user")).isEqualTo(1);
        assertThat(count("app.user_profile")).isEqualTo(1);
        assertThat(count("app.user_identity")).isEqualTo(1);
        assertThat(external.references).hasSize(1);
        assertThat(external.destroyCalls).hasValue(1);
    }

    @Test
    void transactionFailureRollsBackAllRowsAndCleansOnlyCreatedKey() {
        external.identities.put("failure", verified(
                IdentityProvider.GOOGLE, "failure-subject", "failure@example.com", true, "Failure"));
        profiles.failNextSave = true;

        assertThatThrownBy(() -> exchange.exchange(command("failure", installation(1))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("simulated profile failure");

        assertThat(count("app.app_user")).isZero();
        assertThat(count("app.user_profile")).isZero();
        assertThat(count("app.user_identity")).isZero();
        assertThat(count("app.device_registration")).isZero();
        assertThat(count("app.auth_session")).isZero();
        assertThat(count("app.refresh_token_record")).isZero();
        assertThat(external.references).isEmpty();
        assertThat(external.destroyCalls).hasValue(1);
    }

    @Test
    void allExternalCallsOccurOutsideDatabaseTransactions() {
        external.identities.put("boundary", verified(
                IdentityProvider.GOOGLE, "boundary-subject", "boundary@example.com", true, null));
        exchange.exchange(command("boundary", installation(1)));
        exchange.exchange(command("boundary", installation(1)));
        assertThat(external.callInsideTransaction).isFalse();
        assertThat(external.externalCalls).hasValueGreaterThan(0);
    }

    private AuthenticationExchangeCommand command(String token, InstallationId installationId) {
        return new AuthenticationExchangeCommand(token, installationId,
                DevicePlatform.ANDROID, " Test device ", "1.0.0+1");
    }

    private static VerifiedProviderIdentity verified(
            IdentityProvider provider, String subject, String email, boolean verified, String displayName) {
        return new VerifiedProviderIdentity(provider, subject, email, verified, displayName,
                "https://example.test/photo", INITIAL_TIME.minusSeconds(1), INITIAL_TIME.minusSeconds(2));
    }

    private static InstallationId installation(int value) {
        return new InstallationId(new UUID(0, value));
    }

    private static AuthenticationExchangeResult.Success success(AuthenticationExchangeResult result) {
        assertThat(result).isInstanceOf(AuthenticationExchangeResult.Success.class);
        return (AuthenticationExchangeResult.Success) result;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private Instant lastVerified(UserId userId) {
        return jdbc.queryForObject(
                "SELECT last_verified_at FROM app.user_identity WHERE user_id = ?",
                java.sql.Timestamp.class, userId.value()).toInstant();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        @Primary
        MutableClock exchangeClock() {
            return new MutableClock(INITIAL_TIME);
        }

        @Bean
        FakeExternalProviders fakeExternalProviders() {
            return new FakeExternalProviders();
        }

        @Bean
        @Primary
        IdentityLookupProtector testIdentityLookupProtector() {
            byte[] key = new byte[32];
            Arrays.fill(key, (byte) 7);
            return new HmacSha256IdentityLookupProtector(key);
        }

        @Bean
        @Primary
        RefreshTokenGenerator testRefreshTokenGenerator(MutableClock clock) {
            AtomicInteger sequence = new AtomicInteger();
            return () -> new GeneratedRefreshToken(
                    "refresh-" + sequence.incrementAndGet(),
                    clock.instant(),
                    clock.instant().plus(Duration.ofDays(30)));
        }

        @Bean
        @Primary
        RefreshTokenDigester testRefreshTokenDigester() {
            return token -> {
                try {
                    return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
                } catch (java.security.NoSuchAlgorithmException exception) {
                    throw new IllegalStateException(exception);
                }
            };
        }

        @Bean
        @Primary
        AccessTokenIssuer testAccessTokenIssuer(MutableClock clock) {
            AtomicInteger sequence = new AtomicInteger();
            return (userId, sessionId, installationId) -> new IssuedAccessToken(
                    "access-" + sequence.incrementAndGet(), clock.instant(), clock.instant().plus(Duration.ofHours(1)));
        }

        @Bean
        @Primary
        FailingProfileRepository failingProfileRepository(
                @Qualifier("jpaUserProfileRepositoryAdapter") UserProfileRepository delegate) {
            return new FailingProfileRepository(delegate);
        }

        @Bean
        CreateSessionService testCreateSessionService(
                UserAccountRepository users,
                DeviceRegistrationRepository devices,
                AuthSessionRepository sessions,
                RefreshTokenRecordRepository tokens,
                RefreshTokenGenerator generator,
                RefreshTokenDigester digester,
                AccessTokenIssuer issuer,
                IdGenerator ids,
                MutableClock clock) {
            return new CreateSessionService(
                    users, devices, sessions, tokens, generator, digester, issuer, ids, clock);
        }

        @Bean
        AuthenticationExchangeTransactionService testAuthenticationExchangeTransactionService(
                UserAccountRepository users,
                UserIdentityRepository identities,
                UserProfileRepository profiles,
                CreateSessionService sessions,
                MutableClock clock) {
            return new AuthenticationExchangeTransactionService(users, identities, profiles, sessions, clock);
        }

        @Bean
        AuthenticationExchangeService testAuthenticationExchangeService(
                IdentityTokenVerifier verifier,
                IdentityLookupProtector protector,
                UserIdentityRepository identities,
                PersonalDataKeyManager keyManager,
                PersonalDataCipher cipher,
                AuthenticationExchangeTransactionService transactions,
                IdGenerator ids,
                MutableClock clock) {
            return new AuthenticationExchangeService(
                    verifier, protector, identities, keyManager, cipher, transactions, ids, clock);
        }
    }

    static final class MutableClock extends Clock {
        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant value) {
            instant = value;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    static final class FailingProfileRepository implements UserProfileRepository {
        private final UserProfileRepository delegate;
        private volatile boolean failNextSave;

        FailingProfileRepository(UserProfileRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<UserProfile> findByUserId(UserId userId) {
            return delegate.findByUserId(userId);
        }

        @Override
        public UserProfile save(UserProfile profile) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException("simulated profile failure");
            }
            return delegate.save(profile);
        }

        @Override
        public Optional<UserProfile> update(UserProfile profile, long expectedProfileRevision) {
            return delegate.update(profile, expectedProfileRevision);
        }
    }

    static final class FakeExternalProviders
            implements IdentityTokenVerifier, PersonalDataKeyManager, PersonalDataCipher {
        private final ConcurrentHashMap<String, VerifiedProviderIdentity> identities = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<UserId, String> references = new ConcurrentHashMap<>();
        private final AtomicInteger referenceSequence = new AtomicInteger();
        private final AtomicInteger provisionCalls = new AtomicInteger();
        private final AtomicInteger destroyCalls = new AtomicInteger();
        private final AtomicInteger externalCalls = new AtomicInteger();
        private volatile boolean callInsideTransaction;
        private volatile CyclicBarrier provisionBarrier;

        void reset() {
            identities.clear();
            references.clear();
            referenceSequence.set(0);
            provisionCalls.set(0);
            destroyCalls.set(0);
            externalCalls.set(0);
            callInsideTransaction = false;
            provisionBarrier = null;
        }

        @Override
        public VerifiedProviderIdentity verify(String firebaseIdToken) {
            externalCall();
            return Optional.ofNullable(identities.get(firebaseIdToken)).orElseThrow();
        }

        @Override
        public ProvisionedPersonalDataKey provisionKey(UserId userId) {
            externalCall();
            provisionCalls.incrementAndGet();
            CyclicBarrier barrier = provisionBarrier;
            if (barrier != null) {
                try {
                    barrier.await();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
            String candidate = "test-key-" + referenceSequence.incrementAndGet();
            String selected = references.putIfAbsent(userId, candidate);
            return new ProvisionedPersonalDataKey(selected == null ? candidate : selected, selected == null);
        }

        @Override
        public void destroyKey(UserId userId, String keyReference) {
            externalCall();
            references.computeIfPresent(userId, (ignored, existing) -> {
                if (!existing.equals(keyReference)) {
                    throw new IllegalArgumentException("wrong key reference");
                }
                destroyCalls.incrementAndGet();
                return null;
            });
        }

        @Override
        public byte[] encrypt(
                UserId userId, String keyReference, PersonalDataField field, byte[] plaintext) {
            externalCall();
            if (!keyReference.equals(references.get(userId))) {
                throw new IllegalStateException("key unavailable");
            }
            byte[] ciphertext = new byte[plaintext.length + 1];
            ciphertext[0] = (byte) field.ordinal();
            for (int index = 0; index < plaintext.length; index++) {
                ciphertext[index + 1] = (byte) (plaintext[index] ^ 0x5A);
            }
            return ciphertext;
        }

        @Override
        public byte[] decrypt(
                UserId userId, String keyReference, PersonalDataField field, byte[] ciphertext) {
            externalCall();
            if (!keyReference.equals(references.get(userId)) || ciphertext[0] != (byte) field.ordinal()) {
                throw new IllegalStateException("key unavailable");
            }
            byte[] plaintext = new byte[ciphertext.length - 1];
            for (int index = 0; index < plaintext.length; index++) {
                plaintext[index] = (byte) (ciphertext[index + 1] ^ 0x5A);
            }
            return plaintext;
        }

        private void externalCall() {
            externalCalls.incrementAndGet();
            callInsideTransaction |= TransactionSynchronizationManager.isActualTransactionActive();
        }
    }
}
