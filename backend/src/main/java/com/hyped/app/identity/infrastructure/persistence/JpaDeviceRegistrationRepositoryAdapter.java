package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.domain.DeviceId;
import com.hyped.app.identity.domain.DeviceRegistration;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class JpaDeviceRegistrationRepositoryAdapter implements DeviceRegistrationRepository {

    private final SpringDataDeviceRegistrationRepository repository;
    private final DeviceRegistrationPersistenceMapper mapper;

    JpaDeviceRegistrationRepositoryAdapter(
            SpringDataDeviceRegistrationRepository repository, DeviceRegistrationPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<DeviceRegistration> findByUserAndInstallation(UserId userId, InstallationId installationId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(installationId, "installationId");
        return repository.findByUserIdAndInstallationId(userId.value(), installationId.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<DeviceRegistration> findByIdAndUserId(DeviceId deviceId, UserId userId) {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(userId, "userId");
        return repository.findByIdAndUserId(deviceId.value(), userId.value()).map(mapper::toDomain);
    }

    @Override
    public List<DeviceRegistration> findActiveByUserId(UserId userId) {
        Objects.requireNonNull(userId, "userId");
        return repository.findByUserIdAndInvalidatedAtIsNullOrderByLastSeenAtDesc(userId.value())
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countActiveByUserId(UserId userId) {
        Objects.requireNonNull(userId, "userId");
        return repository.countByUserIdAndInvalidatedAtIsNull(userId.value());
    }

    @Override
    @Transactional
    public DeviceRegistration save(DeviceRegistration device) {
        Objects.requireNonNull(device, "device");
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(device)));
    }

    @Override
    @Transactional
    public boolean invalidate(DeviceId deviceId, UserId userId, Instant invalidatedAt) {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(invalidatedAt, "invalidatedAt");
        return repository.invalidate(deviceId.value(), userId.value(), invalidatedAt) > 0;
    }

    @Override
    @Transactional
    public long invalidateWithoutActiveSession(UserId userId, Instant now) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(now, "now");
        return repository.invalidateWithoutActiveSession(userId.value(), now);
    }
}
