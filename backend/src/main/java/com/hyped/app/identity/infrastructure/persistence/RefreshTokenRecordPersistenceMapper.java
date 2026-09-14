package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.RefreshTokenRecord;
import com.hyped.app.identity.domain.RefreshTokenId;
import com.hyped.app.identity.domain.SessionId;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
interface RefreshTokenRecordPersistenceMapper {

    RefreshTokenRecord toDomain(RefreshTokenRecordEntity entity);

    RefreshTokenRecordEntity toEntity(RefreshTokenRecord value);

    default RefreshTokenId toRefreshTokenId(UUID value) {
        return value == null ? null : new RefreshTokenId(value);
    }

    default UUID toUuid(RefreshTokenId id) {
        return id == null ? null : id.value();
    }

    default SessionId toSessionId(UUID value) {
        return value == null ? null : new SessionId(value);
    }

    default UUID toUuid(SessionId id) {
        return id == null ? null : id.value();
    }

    default byte[] copyBytes(byte[] value) {
        return value == null ? null : value.clone();
    }
}
