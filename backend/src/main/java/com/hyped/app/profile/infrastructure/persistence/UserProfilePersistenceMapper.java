package com.hyped.app.profile.infrastructure.persistence;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.profile.domain.UserProfile;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
interface UserProfilePersistenceMapper {

    UserProfile toDomain(UserProfileEntity entity);

    UserProfileEntity toEntity(UserProfile profile);

    default UserId toUserId(UUID value) {
        return value == null ? null : new UserId(value);
    }

    default UUID toUuid(UserId id) {
        return id == null ? null : id.value();
    }

    default byte[] copyBytes(byte[] value) {
        return value == null ? null : value.clone();
    }
}
