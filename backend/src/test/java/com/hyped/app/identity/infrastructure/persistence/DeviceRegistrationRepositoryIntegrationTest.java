package com.hyped.app.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.DeviceId;
import com.hyped.app.identity.domain.DevicePlatform;
import com.hyped.app.identity.domain.DeviceRegistration;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class DeviceRegistrationRepositoryIntegrationTest {

    private static final Instant CREATED = Instant.parse("2026-09-15T10:00:00.123456Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private DeviceRegistrationRepository repository;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeviceRegistrationPersistenceMapper mapper;

    @Test
    void registersWithoutNotificationPermissionUsingFlywaySchema() {
        DeviceRegistration device = device(user(), DevicePlatform.ANDROID, CREATED, null, null);

        assertThat(repository.save(device)).isEqualTo(device);
        entityManager.clear();

        DeviceRegistration loaded = repository.findByIdAndUserId(device.id(), device.userId()).orElseThrow();
        assertThat(loaded).isEqualTo(device);
        assertThat(loaded.notificationsEnabled()).isFalse();
        assertThat(loaded.fcmTokenCiphertext()).isNull();
        assertThat(loaded.fcmTokenFingerprint()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT success FROM flyway.flyway_schema_history WHERE version = '2'", Boolean.class)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"ANDROID, android", "IOS, ios"})
    void registersFcmValuesAndStoresLowercasePlatform(DevicePlatform platform, String storedPlatform) {
        DeviceRegistration device = device(
                user(), platform, CREATED.plusSeconds(20), new byte[] {1, 2, 3}, new byte[32]);

        assertThat(repository.save(device)).isEqualTo(device);
        entityManager.clear();

        assertThat(repository.findByIdAndUserId(device.id(), device.userId())).contains(device);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT platform FROM app.device_registration WHERE id = ?", String.class, device.id().value()))
                .isEqualTo(storedPlatform);
    }

    @Test
    void lookupsAreScopedToUserAndInstallation() {
        UserId owner = user();
        UserId other = user();
        DeviceRegistration device = repository.save(device(owner, DevicePlatform.IOS, CREATED, null, null));
        entityManager.clear();

        assertThat(repository.findByUserAndInstallation(owner, device.installationId())).contains(device);
        assertThat(repository.findByUserAndInstallation(other, device.installationId())).isEmpty();
        assertThat(repository.findByIdAndUserId(device.id(), other)).isEmpty();
        assertThat(repository.findByUserAndInstallation(owner, new InstallationId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void countsAndListsOnlyUsersActiveDevicesInDescendingActivityOrder() {
        UserId owner = user();
        DeviceRegistration oldest = repository.save(device(owner, DevicePlatform.ANDROID, CREATED, null, null));
        DeviceRegistration newest = repository.save(
                device(owner, DevicePlatform.IOS, CREATED.plusSeconds(30), null, null));
        DeviceRegistration retired = repository.save(
                device(owner, DevicePlatform.IOS, CREATED.plusSeconds(60), null, null));
        UserId other = user();
        repository.save(device(other, DevicePlatform.IOS, CREATED.plusSeconds(90), null, null));
        assertThat(repository.invalidate(retired.id(), owner, CREATED.plusSeconds(70))).isTrue();

        assertThat(repository.countActiveByUserId(owner)).isEqualTo(2);
        assertThat(repository.findActiveByUserId(owner)).containsExactly(newest, oldest);
        assertThat(repository.countActiveByUserId(other)).isEqualTo(1);
        UserId absent = new UserId(UUID.randomUUID());
        assertThat(repository.countActiveByUserId(absent)).isZero();
        assertThat(repository.findActiveByUserId(absent)).isEmpty();
    }

    @Test
    void invalidationUpdatesStateAndAuditTimeAndReturnsFalseOnRepeat() {
        DeviceRegistration device = repository.save(device(user(), DevicePlatform.IOS, CREATED, null, null));
        // Populate the persistence context before the bulk update to check stale state is cleared.
        repository.findByIdAndUserId(device.id(), device.userId()).orElseThrow();
        Instant invalidatedAt = CREATED.plusSeconds(60);

        assertThat(repository.invalidate(device.id(), device.userId(), invalidatedAt)).isTrue();
        DeviceRegistration invalidated = repository.findByIdAndUserId(device.id(), device.userId()).orElseThrow();
        assertThat(invalidated.isActive()).isFalse();
        assertThat(invalidated.invalidatedAt()).isEqualTo(invalidatedAt);
        assertThat(invalidated.updatedAt()).isEqualTo(invalidatedAt);
        assertThat(repository.findByUserAndInstallation(device.userId(), device.installationId()))
                .contains(invalidated);
        assertThat(repository.invalidate(device.id(), device.userId(), invalidatedAt.plusSeconds(10))).isFalse();
        assertThat(repository.findByIdAndUserId(device.id(), device.userId())).contains(invalidated);
    }

    @Test
    void invalidationCannotAffectAnotherUsersDevice() {
        DeviceRegistration device = repository.save(device(user(), DevicePlatform.ANDROID, CREATED, null, null));

        assertThat(repository.invalidate(device.id(), user(), CREATED.plusSeconds(60))).isFalse();
        assertThat(repository.findByIdAndUserId(device.id(), device.userId())).contains(device);
        assertThat(repository.invalidate(new DeviceId(UUID.randomUUID()), device.userId(), CREATED)).isFalse();
    }

    @Test
    void invalidationCannotPrecedeCreationOrMoveAuditTimeBackwards() {
        DeviceRegistration device = repository.save(
                device(user(), DevicePlatform.ANDROID, CREATED.plusSeconds(90), null, null));

        assertThat(repository.invalidate(device.id(), device.userId(), CREATED.minusSeconds(1))).isFalse();
        assertThat(repository.findByIdAndUserId(device.id(), device.userId())).contains(device);
        assertThat(repository.invalidate(device.id(), device.userId(), CREATED.plusSeconds(60))).isTrue();
        assertThat(repository.findByIdAndUserId(device.id(), device.userId()).orElseThrow().updatedAt())
                .isEqualTo(device.updatedAt());
    }

    @Test
    void fcmArraysAreCopiedAcrossDomainEntityMapperAndPersistenceBoundaries() {
        byte[] ciphertext = {1, 2, 3};
        byte[] fingerprint = new byte[32];
        DeviceRegistration device = device(user(), DevicePlatform.ANDROID, CREATED, ciphertext, fingerprint);
        ciphertext[0] = 99;
        fingerprint[0] = 99;
        DeviceRegistrationEntity entity = mapper.toEntity(device);
        byte[] entityInput = {1, 2, 3};
        byte[] fingerprintInput = new byte[32];
        entity.setFcmTokenCiphertext(entityInput);
        entity.setFcmTokenFingerprint(fingerprintInput);
        entityInput[0] = 88;
        fingerprintInput[0] = 88;
        entity.getFcmTokenCiphertext()[0] = 77;
        entity.getFcmTokenFingerprint()[0] = 77;
        DeviceRegistration mapped = mapper.toDomain(entity);
        entity.setFcmTokenCiphertext(new byte[] {9});
        entity.setFcmTokenFingerprint(new byte[32]);
        assertThat(mapped).isEqualTo(device);

        DeviceRegistration saved = repository.save(mapped);
        saved.fcmTokenCiphertext()[0] = 66;
        saved.fcmTokenFingerprint()[0] = 66;
        device.fcmTokenCiphertext()[0] = 55;
        device.fcmTokenFingerprint()[0] = 55;
        entityManager.flush();
        entityManager.clear();
        DeviceRegistration loaded = repository.findByIdAndUserId(device.id(), device.userId()).orElseThrow();
        assertThat(loaded.fcmTokenCiphertext()).containsExactly(1, 2, 3);
        assertThat(loaded.fcmTokenFingerprint()).containsExactly(new byte[32]);
        loaded.fcmTokenCiphertext()[0] = 44;
        loaded.fcmTokenFingerprint()[0] = 44;
        entityManager.flush();
        entityManager.clear();
        assertThat(repository.findByIdAndUserId(device.id(), device.userId())).contains(device);
    }

    private UserId user() {
        UserId id = new UserId(UUID.randomUUID());
        accounts.save(new UserAccount(id, AccountStatus.ACTIVE, null, 0, null, null, null, null, CREATED, CREATED));
        return id;
    }

    private static DeviceRegistration device(
            UserId userId, DevicePlatform platform, Instant lastSeenAt, byte[] ciphertext, byte[] fingerprint) {
        return new DeviceRegistration(new DeviceId(UUID.randomUUID()), userId, platform,
                new InstallationId(UUID.randomUUID()), "Test device", ciphertext, fingerprint,
                ciphertext != null, lastSeenAt, null, CREATED, lastSeenAt);
    }
}
