package com.hyped.app.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyped.app.identity.application.model.ActiveSessionSummary;
import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.application.port.out.RefreshTokenRecordRepository;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"hyped.tokens.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers(disabledWithoutDocker = true)
@Import(ActiveDeviceManagementIntegrationTest.Configuration.class)
class ActiveDeviceManagementIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ListActiveSessionsService listService;

    @Autowired
    private RevokeSessionService revokeService;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private DeviceRegistrationRepository devices;

    @Autowired
    private AuthSessionRepository sessions;

    @Autowired
    private RefreshTokenRecordRepository tokens;

    @Test
    void listsOnlyOwnersActiveSessionsNewestFirstAndMarksCurrent() {
        UserId owner = user();
        Fixture oldest = fixture(owner, "Pixel", DevicePlatform.ANDROID, NOW.minusSeconds(300));
        Fixture current = fixture(owner, "iPhone", DevicePlatform.IOS, NOW.minusSeconds(60));
        fixture(user(), "Other user's device", DevicePlatform.ANDROID, NOW.minusSeconds(10));

        List<ActiveSessionSummary> result = listService.list(owner, current.session().id());

        assertThat(result).extracting(ActiveSessionSummary::sessionId)
                .containsExactly(current.session().id(), oldest.session().id());
        assertThat(result).extracting(ActiveSessionSummary::current).containsExactly(true, false);
        assertThat(result.getFirst().deviceName()).isEqualTo("iPhone");
        assertThat(result.getFirst().platform()).isEqualTo(DevicePlatform.IOS);
        assertThat(result.getFirst().createdAt()).isEqualTo(current.session().createdAt());
        assertThat(result.getFirst().lastUsedAt()).isEqualTo(current.session().lastUsedAt());
    }

    @Test
    void revocationIsOwnerScopedRevokesTokensReleasesSlotAndIsRepeatable() {
        UserId owner = user();
        Fixture fixture = fixture(owner, "Pixel", DevicePlatform.ANDROID, NOW.minusSeconds(60));
        UserId otherUser = user();
        Fixture other = fixture(otherUser, "Other", DevicePlatform.IOS, NOW.minusSeconds(30));

        assertThat(revokeService.revoke(otherUser, fixture.session().id())).isFalse();
        assertThat(sessions.findById(fixture.session().id())).contains(fixture.session());
        assertThat(tokens.findBySessionId(fixture.session().id())).containsExactly(fixture.token());

        assertThat(revokeService.revoke(owner, fixture.session().id())).isTrue();
        assertThat(revokeService.revoke(owner, fixture.session().id())).isTrue();

        AuthSession revoked = sessions.findById(fixture.session().id()).orElseThrow();
        assertThat(revoked.revokedAt()).isEqualTo(NOW);
        assertThat(revoked.revokeReason()).isEqualTo("device_removed");
        assertThat(tokens.findBySessionId(fixture.session().id()))
                .allMatch(token -> token.state() == RefreshTokenState.REVOKED);
        assertThat(devices.findByIdAndUserId(fixture.device().id(), owner).orElseThrow().isActive()).isFalse();
        assertThat(devices.countActiveByUserId(owner)).isZero();
        assertThat(sessions.findById(other.session().id())).contains(other.session());
        assertThat(devices.findByIdAndUserId(other.device().id(), otherUser).orElseThrow().isActive()).isTrue();
    }

    private UserId user() {
        UserId userId = new UserId(UUID.randomUUID());
        users.save(new UserAccount(userId, AccountStatus.ACTIVE, null, 0, null,
                null, null, null, NOW.minus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(1))));
        return userId;
    }

    private Fixture fixture(UserId owner, String name, DevicePlatform platform, Instant lastUsedAt) {
        Instant issuedAt = lastUsedAt.minusSeconds(30);
        DeviceRegistration device = devices.save(new DeviceRegistration(new DeviceId(UUID.randomUUID()), owner,
                platform, new InstallationId(UUID.randomUUID()), name, null, null, false,
                lastUsedAt, null, issuedAt, lastUsedAt));
        AuthSession session = sessions.save(new AuthSession(new SessionId(UUID.randomUUID()), owner, device.id(),
                new TokenFamilyId(UUID.randomUUID()), issuedAt, lastUsedAt, NOW.plus(Duration.ofDays(30)),
                null, null, issuedAt, lastUsedAt));
        byte[] digest = new byte[32];
        UUID digestSource = UUID.randomUUID();
        putLong(digest, 0, digestSource.getMostSignificantBits());
        putLong(digest, 8, digestSource.getLeastSignificantBits());
        putLong(digest, 16, session.id().value().getMostSignificantBits());
        putLong(digest, 24, session.id().value().getLeastSignificantBits());
        RefreshTokenRecord token = tokens.save(new RefreshTokenRecord(new RefreshTokenId(UUID.randomUUID()),
                session.id(), digest, RefreshTokenState.ACTIVE, issuedAt, null,
                session.expiresAt(), null, issuedAt));
        return new Fixture(device, session, token);
    }

    private static void putLong(byte[] target, int offset, long value) {
        for (int index = 0; index < Long.BYTES; index++) {
            target[offset + index] = (byte) (value >>> (Long.SIZE - Byte.SIZE * (index + 1)));
        }
    }

    private record Fixture(
            DeviceRegistration device, AuthSession session, RefreshTokenRecord token) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
