package com.hyped.app.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.hyped.app.identity.application.model.CreateSessionCommand;
import com.hyped.app.identity.application.model.CreateSessionResult;
import com.hyped.app.identity.application.model.SessionTokenPair;
import com.hyped.app.identity.application.port.out.AccessTokenIssuer;
import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.application.port.out.IdGenerator;
import com.hyped.app.identity.application.port.out.RefreshTokenDigester;
import com.hyped.app.identity.application.port.out.RefreshTokenGenerator;
import com.hyped.app.identity.application.port.out.RefreshTokenRecordRepository;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.application.service.CreateSessionService;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.AuthSession;
import com.hyped.app.identity.domain.DevicePlatform;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.RefreshTokenState;
import com.hyped.app.identity.domain.SessionId;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"hyped.tokens.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers(disabledWithoutDocker = true)
@Import(CreateSessionServiceIntegrationTest.Configuration.class)
class CreateSessionServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private CreateSessionService service;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private DeviceRegistrationRepository devices;

    @Autowired
    private AuthSessionRepository sessions;

    @Autowired
    private RefreshTokenRecordRepository tokens;

    @Autowired
    private RefreshTokenDigester digester;

    @Autowired
    private TestClock clock;

    @MockitoSpyBean
    private AccessTokenIssuer issuer;

    @BeforeEach
    void resetClock() {
        clock.set(NOW);
    }

    @Test
    void firstSignInCreatesDeviceSessionAndUsableTokens() {
        UserId userId = user(AccountStatus.ACTIVE, null, 3);
        InstallationId installationId = installation();

        CreateSessionResult result = service.create(command(userId, installationId, "Pixel 10"));

        assertThat(result).isInstanceOf(CreateSessionResult.Success.class);
        SessionTokenPair pair = ((CreateSessionResult.Success) result).tokens();
        Jwt jwt = decoder().decode(pair.accessToken().tokenValue());
        SessionId sessionId = new SessionId(UUID.fromString(jwt.getClaimAsString("sid")));
        AuthSession session = sessions.findById(sessionId).orElseThrow();
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("did")).isEqualTo(installationId.toString());
        assertThat(Duration.between(pair.accessToken().issuedAt(), pair.accessToken().expiresAt()))
                .isEqualTo(Duration.ofHours(1));
        assertThat(Duration.between(pair.refreshToken().issuedAt(), pair.refreshToken().expiresAt()))
                .isEqualTo(Duration.ofDays(30));
        assertThat(session.deviceId()).isEqualTo(devices.findByUserAndInstallation(userId, installationId)
                .orElseThrow().id());
        assertThat(tokens.findBySessionId(sessionId)).singleElement().satisfies(token -> {
            assertThat(token.state()).isEqualTo(RefreshTokenState.ACTIVE);
            assertThat(token.tokenDigest()).containsExactly(digester.digest(pair.refreshToken().tokenValue()));
        });
        assertThat(users.findById(userId).orElseThrow().failedAuthCount()).isZero();
        assertThat(result.toString()).doesNotContain(
                pair.accessToken().tokenValue(), pair.refreshToken().tokenValue());
    }

    @Test
    void repeatedSignInReusesDeviceSlotAndRevokesItsPreviousSession() {
        UserId userId = user(AccountStatus.ACTIVE, null, 0);
        InstallationId installationId = installation();
        CreateSessionResult.Success first = success(service.create(command(userId, installationId, "iPhone")));
        SessionId firstSessionId = sessionId(first);

        CreateSessionResult.Success second = success(service.create(command(userId, installationId, "iPhone 18")));

        assertThat(devices.countActiveByUserId(userId)).isEqualTo(1);
        assertThat(devices.findByUserAndInstallation(userId, installationId).orElseThrow().deviceName())
                .isEqualTo("iPhone 18");
        assertThat(sessions.findById(firstSessionId).orElseThrow().revokeReason()).isEqualTo("new_login");
        assertThat(tokens.findBySessionId(firstSessionId))
                .allMatch(token -> token.state() == RefreshTokenState.REVOKED);
        assertThat(sessions.findActiveByUserId(userId, NOW)).singleElement()
                .extracting(AuthSession::id).isEqualTo(sessionId(second));
    }

    @Test
    void fifthDeviceSucceedsAndSixthReturnsSummariesWithoutCreatingSession() {
        UserId userId = user(AccountStatus.ACTIVE, null, 0);
        for (int index = 1; index <= 4; index++) {
            success(service.create(command(userId, installation(), "Device " + index)));
        }

        success(service.create(command(userId, installation(), "Device 5")));
        long sessionsBeforeRejection = sessions.findActiveByUserId(userId, NOW).size();
        CreateSessionResult result = service.create(command(userId, installation(), "Device 6"));

        assertThat(result).isInstanceOf(CreateSessionResult.DeviceLimitReached.class);
        List<?> summaries = ((CreateSessionResult.DeviceLimitReached) result).devices();
        assertThat(summaries).hasSize(5);
        assertThat(devices.countActiveByUserId(userId)).isEqualTo(5);
        assertThat(sessions.findActiveByUserId(userId, NOW)).hasSize((int) sessionsBeforeRejection);
    }

    @Test
    void expiredLockIsClearedWhenSessionIsCreated() {
        UserId userId = user(AccountStatus.LOCKED, NOW.minusSeconds(1), 5);

        assertThat(service.create(command(userId, installation(), "Recovered device")))
                .isInstanceOf(CreateSessionResult.Success.class);

        UserAccount updated = users.findById(userId).orElseThrow();
        assertThat(updated.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(updated.lockedUntil()).isNull();
        assertThat(updated.failedAuthCount()).isZero();
    }

    @Test
    void activeLockIsRejectedWithoutCreatingDeviceOrSession() {
        UserId userId = user(AccountStatus.LOCKED, NOW.plusSeconds(900), 5);

        CreateSessionResult result = service.create(command(userId, installation(), "Blocked device"));

        assertThat(result).isEqualTo(new CreateSessionResult.AccountLocked(NOW.plusSeconds(900)));
        assertThat(devices.countActiveByUserId(userId)).isZero();
        assertThat(sessions.findActiveByUserId(userId, NOW)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"SUSPENDED", "COMPROMISED"})
    void blockedAccountsCannotCreateSessions(AccountStatus status) {
        UserId userId = user(status, null, 0);

        assertThat(service.create(command(userId, installation(), "Blocked device")))
                .isEqualTo(new CreateSessionResult.AccountBlocked(status));
        assertThat(devices.countActiveByUserId(userId)).isZero();
    }

    @Test
    void tokenSigningFailureRollsBackAccountDeviceSessionAndRefreshToken() {
        UserId userId = user(AccountStatus.ACTIVE, null, 4);
        doThrow(new IllegalStateException("Test signing failure")).when(issuer).issue(any(), any(), any());

        assertThatThrownBy(() -> service.create(command(userId, installation(), "Rollback device")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(users.findById(userId).orElseThrow().failedAuthCount()).isEqualTo(4);
        assertThat(devices.countActiveByUserId(userId)).isZero();
        assertThat(sessions.findActiveByUserId(userId, NOW)).isEmpty();
    }

    private UserId user(AccountStatus status, Instant lockedUntil, int failures) {
        UserId id = new UserId(UUID.randomUUID());
        Instant createdAt = NOW.minus(Duration.ofDays(1));
        users.save(new UserAccount(id, status, lockedUntil, failures, null, null, null, null,
                createdAt, createdAt));
        return id;
    }

    private static CreateSessionCommand command(UserId userId, InstallationId installationId, String deviceName) {
        return new CreateSessionCommand(userId, installationId, DevicePlatform.ANDROID,
                deviceName, null, null, false);
    }

    private static InstallationId installation() {
        return new InstallationId(UUID.randomUUID());
    }

    private CreateSessionResult.Success success(CreateSessionResult result) {
        assertThat(result).isInstanceOf(CreateSessionResult.Success.class);
        return (CreateSessionResult.Success) result;
    }

    private SessionId sessionId(CreateSessionResult.Success result) {
        String value = decoder().decode(result.tokens().accessToken().tokenValue()).getClaimAsString("sid");
        return new SessionId(UUID.fromString(value));
    }

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) TestTokenKeys.PAIR.getPublic()).build();
        JwtTimestampValidator validator = new JwtTimestampValidator(Duration.ZERO);
        validator.setClock(clock);
        decoder.setJwtValidator(validator);
        return decoder;
    }

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
        CreateSessionService createSessionService(UserAccountRepository users,
                DeviceRegistrationRepository devices, AuthSessionRepository sessions,
                RefreshTokenRecordRepository tokens, RefreshTokenGenerator generator,
                RefreshTokenDigester digester, AccessTokenIssuer issuer, IdGenerator ids, Clock clock) {
            return new CreateSessionService(users, devices, sessions, tokens, generator, digester, issuer, ids, clock);
        }
    }
}
