package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.IdentityId;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.identity.domain.UserIdentity;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
interface UserIdentityPersistenceMapper {

    UserIdentity toDomain(UserIdentityEntity entity);

    UserIdentityEntity toEntity(UserIdentity identity);

    default IdentityId toIdentityId(UUID value) {
        return value == null ? null : new IdentityId(value);
    }

    default UserId toUserId(UUID value) {
        return value == null ? null : new UserId(value);
    }

    default UUID toUuid(IdentityId id) {
        return id == null ? null : id.value();
    }

    default UUID toUuid(UserId id) {
        return id == null ? null : id.value();
    }

    default byte[] copyBytes(byte[] value) {
        return value == null ? null : value.clone();
    }
}
