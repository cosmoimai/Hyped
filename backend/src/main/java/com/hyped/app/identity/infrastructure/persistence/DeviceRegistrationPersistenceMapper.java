package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.DeviceId;
import com.hyped.app.identity.domain.DeviceRegistration;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.UserId;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
interface DeviceRegistrationPersistenceMapper {

    DeviceRegistration toDomain(DeviceRegistrationEntity entity);

    DeviceRegistrationEntity toEntity(DeviceRegistration device);

    default DeviceId toDeviceId(UUID value) {
        return value == null ? null : new DeviceId(value);
    }

    default UserId toUserId(UUID value) {
        return value == null ? null : new UserId(value);
    }

    default InstallationId toInstallationId(UUID value) {
        return value == null ? null : new InstallationId(value);
    }

    default UUID toUuid(DeviceId id) {
        return id == null ? null : id.value();
    }

    default UUID toUuid(UserId id) {
        return id == null ? null : id.value();
    }

    default UUID toUuid(InstallationId id) {
        return id == null ? null : id.value();
    }

    default byte[] copyBytes(byte[] value) {
        return value == null ? null : value.clone();
    }
}
