package com.hyped.app.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.hyped.app.identity.application.model.GeneratedRefreshToken;
import com.hyped.app.identity.application.model.IssuedAccessToken;
import com.hyped.app.identity.application.model.RefreshSessionResult;
import com.hyped.app.identity.application.model.SessionTokenPair;
import com.hyped.app.identity.application.port.out.AccessTokenIssuer;
import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.application.port.out.IdGenerator;
import com.hyped.app.identity.application.port.out.RefreshTokenDigester;
import com.hyped.app.identity.application.port.out.RefreshTokenGenerator;
import com.hyped.app.identity.application.port.out.RefreshTokenRecordRepository;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.application.service.LogoutSessionService;
import com.hyped.app.identity.application.service.RefreshSessionService;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.AuthSession;
import com.hyped.app.identity.domain.DeviceId;
import com.hyped.app.identity.domain.DevicePlatform;
import com.hyped.app.identity.domain.DeviceRegistration;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.RefreshTokenId;
import com.hyped.app.identity.domain.RefreshTokenRecord;
import com.hyped.app.identity.domain.RefreshTokenState;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.TokenFamilyId;
import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"hyped.tokens.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers(disabledWithoutDocker = true)
@Import(RefreshSessionServiceIntegrationTest.Configuration.class)
class RefreshSessionServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private RefreshSessionService service;

    @Autowired
    private LogoutSessionService logout;

    @Autowired
    private AuthSessionRepository sessions;

    @Autowired
    private RefreshTokenRecordRepository tokens;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private DeviceRegistrationRepository devices;

    @Autowired
    private RefreshTokenGenerator generator;

    @Autowired
    private RefreshTokenDigester digester;

    @Autowired
    private TestClock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AccessTokenIssuer issuer;

    @BeforeEach
    void resetClock() {
        clock.set(NOW);
    }

    @Test
    void successfulRotationCommitsConsumedPredecessorOneActiveReplacementAndSessionActivity() {
        Fixture fixture = fixture(user());

        RefreshSessionResult result = refresh(fixture);

        assertThat(result).isInstanceOf(RefreshSessionResult.Success.class);
        SessionTokenPair pair = ((RefreshSessionResult.Success) result).tokens();
        List<RefreshTokenRecord> records = tokens.findBySessionId(fixture.session().id());
        assertThat(records).hasSize(2);
        RefreshTokenRecord original = records.stream().filter(t -> t.id().equals(fixture.token().id()))
                .findFirst().orElseThrow();
        RefreshTokenRecord replacement = records.stream().filter(t -> t.state() == RefreshTokenState.ACTIVE)
                .findFirst().orElseThrow();
        assertThat(original.state()).isEqualTo(RefreshTokenState.CONSUMED);
        assertThat(original.consumedAt()).isEqualTo(NOW);
        assertThat(original.replacedById()).isEqualTo(replacement.id());
        assertThat(records.stream().filter(t -> t.state() == RefreshTokenState.ACTIVE)).hasSize(1);
        assertThat(replacement.tokenDigest()).containsExactly(digester.digest(pair.refreshToken().tokenValue()));
        assertThat(replacement.expiresAt()).isEqualTo(fixture.session().expiresAt());
        assertThat(pair.refreshToken().expiresAt()).isEqualTo(replacement.expiresAt());
        assertThat(pair.refreshToken().tokenValue()).isNotEqualTo(fixture.refresh().tokenValue());
        assertThat(sessions.findById(fixture.session().id()).orElseThrow().lastUsedAt()).isEqualTo(NOW);
        assertThat(sessions.findById(fixture.session().id()).orElseThrow().updatedAt()).isEqualTo(NOW);
        assertThat(Duration.between(pair.accessToken().issuedAt(), pair.accessToken().expiresAt()))
                .isEqualTo(Duration.ofHours(1));
        assertThat(decoder().decode(pair.accessToken().tokenValue()).getClaimAsString("sid"))
                .isEqualTo(fixture.session().id().toString());
        assertThat(result.toString()).doesNotContain(pair.refreshToken().tokenValue(), pair.accessToken().tokenValue());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT condeferrable AND condeferred FROM pg_constraint
                WHERE conrelid = 'app.refresh_token_record'::regclass
                    AND conname = 'refresh_token_record_replacement_fk'
                """, Boolean.class)).isTrue();
    }

    @Test
    void reuseCommitsRevocationOnlyForAffectedSession() {
        UserId owner = user();
        Fixture affected = fixture(owner);
        Fixture other = fixture(owner);
        SessionTokenPair replacement = ((RefreshSessionResult.Success) refresh(affected)).tokens();

        assertThat(refresh(affected)).isEqualTo(
                new RefreshSessionResult.ReauthenticationRequired("REFRESH_TOKEN_REUSE_DETECTED"));

        AuthSession revoked = sessions.findById(affected.session().id()).orElseThrow();
        assertThat(revoked.revokedAt()).isEqualTo(NOW);
        assertThat(revoked.revokeReason()).isEqualTo("refresh_token_reuse");
        assertThat(tokens.findBySessionId(revoked.id())).hasSize(2)
                .allMatch(token -> token.state() == RefreshTokenState.REVOKED);
        assertThat(service.refresh(replacement.refreshToken().tokenValue(), affected.device().installationId()))
                .isEqualTo(new RefreshSessionResult.ReauthenticationRequired("SESSION_EXPIRED"));
        assertThat(sessions.findById(other.session().id())).contains(other.session());
        assertThat(tokens.findBySessionId(other.session().id())).containsExactly(other.token());
        assertThat(devices.findByIdAndUserId(other.device().id(), owner)).contains(other.device());
        assertThat(refresh(other)).isInstanceOf(RefreshSessionResult.Success.class);
    }

    @Test
    void logoutRevokesRefreshCapabilityButExistingJwtRemainsCryptographicallyValid() {
        Fixture fixture = fixture(user());
        IssuedAccessToken access = issuer.issue(fixture.session().userId(), fixture.session().id(),
                fixture.device().installationId());
        refresh(fixture);

        assertThat(logout.logout(fixture.session().userId(), fixture.session().id())).isTrue();
        assertThat(logout.logout(fixture.session().userId(), fixture.session().id())).isTrue();

        assertThat(tokens.findBySessionId(fixture.session().id()))
                .allMatch(token -> token.state() == RefreshTokenState.REVOKED);
        assertThat(sessions.findById(fixture.session().id()).orElseThrow().revokeReason()).isEqualTo("logout");
        assertThat(refresh(fixture)).isEqualTo(new RefreshSessionResult.ReauthenticationRequired("SESSION_EXPIRED"));
        assertThat(decoder().decode(access.tokenValue()).getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void logoutCannotRevokeAnotherUsersSession() {
        Fixture fixture = fixture(user());
        assertThat(logout.logout(user(), fixture.session().id())).isFalse();
        assertThat(sessions.findById(fixture.session().id())).contains(fixture.session());
        assertThat(tokens.findBySessionId(fixture.session().id())).containsExactly(fixture.token());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class,
            names = {"SUSPENDED", "COMPROMISED", "DELETION_PENDING", "DELETED", "LOCKED"})
    void unavailableAccountsCannotRefresh(AccountStatus status) {
        Fixture fixture = fixture(user());
        UserAccount account = users.findById(fixture.session().userId()).orElseThrow();
        users.save(new UserAccount(account.id(), status, status == AccountStatus.LOCKED ? NOW.plusSeconds(900) : null,
                0, null, status == AccountStatus.DELETION_PENDING ? NOW : null,
                status == AccountStatus.DELETED ? NOW : null, status == AccountStatus.DELETED ? NOW : null,
                account.createdAt(), NOW));

        assertThat(refresh(fixture))
                .isEqualTo(new RefreshSessionResult.ReauthenticationRequired("ACCOUNT_UNAVAILABLE"));
        assertThat(tokens.findBySessionId(fixture.session().id())).containsExactly(fixture.token());
        assertThat(sessions.findById(fixture.session().id())).contains(fixture.session());
    }

    @Test
    void wrongInstallationAndUnknownTokenCannotRefresh() {
        Fixture fixture = fixture(user());
        assertThat(service.refresh(fixture.refresh().tokenValue(), new InstallationId(UUID.randomUUID())))
                .isEqualTo(new RefreshSessionResult.ReauthenticationRequired("SESSION_DEVICE_MISMATCH"));
        assertThat(service.refresh(generator.generate().tokenValue(), fixture.device().installationId()))
                .isEqualTo(new RefreshSessionResult.ReauthenticationRequired("SESSION_EXPIRED"));
        assertThat(tokens.findBySessionId(fixture.session().id())).containsExactly(fixture.token());
    }

    @Test
    void expiryBoundaryRejectsRefresh() {
        Fixture fixture = fixture(user());
        clock.set(fixture.session().expiresAt());
        assertThat(refresh(fixture)).isEqualTo(new RefreshSessionResult.ReauthenticationRequired("SESSION_EXPIRED"));
        assertThat(tokens.findBySessionId(fixture.session().id())).containsExactly(fixture.token());
    }

    @Test
    void accessTokenSigningFailureRollsBackTheEntireRotation() {
        Fixture fixture = fixture(user());
        doThrow(new IllegalStateException("Test signing failure")).when(issuer).issue(any(), any(), any());

        assertThatThrownBy(() -> refresh(fixture)).isInstanceOf(IllegalStateException.class);
        assertThat(tokens.findBySessionId(fixture.session().id())).containsExactly(fixture.token());
        assertThat(sessions.findById(fixture.session().id())).contains(fixture.session());
    }

    @Test
    void simultaneousRefreshesSerializeAndTheReplayRevokesTheFamily() throws Exception {
        Fixture fixture = fixture(user());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return refresh(fixture);
            });
            var second = executor.submit(() -> {
                start.await();
                return refresh(fixture);
            });
            start.countDown();
            List<RefreshSessionResult> results = List.of(
                    first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().filter(RefreshSessionResult.Success.class::isInstance)).hasSize(1);
            assertThat(results)
                    .contains(new RefreshSessionResult.ReauthenticationRequired("REFRESH_TOKEN_REUSE_DETECTED"));
        }
        assertThat(tokens.findBySessionId(fixture.session().id()))
                .allMatch(token -> token.state() == RefreshTokenState.REVOKED);
    }

    private RefreshSessionResult refresh(Fixture fixture) {
        return service.refresh(fixture.refresh().tokenValue(), fixture.device().installationId());
    }

    private UserId user() {
        UserId id = new UserId(UUID.randomUUID());
        users.save(new UserAccount(id, AccountStatus.ACTIVE, null, 0, null, null, null, null,
                NOW.minusSeconds(60), NOW.minusSeconds(60)));
        return id;
    }

    private Fixture fixture(UserId owner) {
        Instant issued = NOW.minusSeconds(60);
        DeviceRegistration device = devices.save(new DeviceRegistration(new DeviceId(UUID.randomUUID()), owner,
                DevicePlatform.ANDROID, new InstallationId(UUID.randomUUID()), "Test device", null, null, false,
                issued, null, issued, issued));
        AuthSession session = sessions.save(new AuthSession(new SessionId(UUID.randomUUID()), owner, device.id(),
                new TokenFamilyId(UUID.randomUUID()), issued, issued, issued.plus(Duration.ofDays(30)),
                null, null, issued, issued));
        GeneratedRefreshToken generated = generator.generate();
        RefreshTokenRecord token = tokens.save(new RefreshTokenRecord(new RefreshTokenId(UUID.randomUUID()),
                session.id(), digester.digest(generated.tokenValue()), RefreshTokenState.ACTIVE, NOW,
                null, session.expiresAt(), null, NOW));
        return new Fixture(device, session, generated, token);
    }

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) TestTokenKeys.PAIR.getPublic()).build();
        JwtTimestampValidator validator = new JwtTimestampValidator(Duration.ZERO);
        validator.setClock(clock);
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private record Fixture(DeviceRegistration device, AuthSession session,
            GeneratedRefreshToken refresh, RefreshTokenRecord token) {}

    static final class TestClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(NOW);

        void set(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }

        @Bean
        AccessTokenIssuer testAccessTokenIssuer(Clock clock) throws Exception {
            TokenProperties properties = TestTokenKeys.properties();
            return new Rs256AccessTokenIssuer(new TokenConfiguration().jwtEncoder(properties), clock, properties);
        }

        @Bean
        RefreshSessionService refreshSessionService(RefreshTokenDigester digester, RefreshTokenRecordRepository tokens,
                AuthSessionRepository sessions, UserAccountRepository users, DeviceRegistrationRepository devices,
                RefreshTokenGenerator generator, AccessTokenIssuer issuer, IdGenerator ids, Clock clock) {
            return new RefreshSessionService(digester, tokens, sessions, users, devices, generator, issuer, ids, clock);
        }
    }
}
