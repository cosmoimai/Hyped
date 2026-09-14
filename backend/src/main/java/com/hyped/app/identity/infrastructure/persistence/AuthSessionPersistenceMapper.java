package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.AuthSession;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.identity.domain.DeviceId;
import com.hyped.app.identity.domain.TokenFamilyId;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
interface AuthSessionPersistenceMapper {

    AuthSession toDomain(AuthSessionEntity entity);

    AuthSessionEntity toEntity(AuthSession value);

    default SessionId toSessionId(UUID value) {
        return value == null ? null : new SessionId(value);
    }

    default UUID toUuid(SessionId id) {
        return id == null ? null : id.value();
    }

    default UserId toUserId(UUID value) {
        return value == null ? null : new UserId(value);
    }

    default UUID toUuid(UserId id) {
        return id == null ? null : id.value();
    }

    default DeviceId toDeviceId(UUID value) {
        return value == null ? null : new DeviceId(value);
    }

    default UUID toUuid(DeviceId id) {
        return id == null ? null : id.value();
    }

    default TokenFamilyId toTokenFamilyId(UUID value) {
        return value == null ? null : new TokenFamilyId(value);
    }

    default UUID toUuid(TokenFamilyId id) {
        return id == null ? null : id.value();
    }
}
