package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
interface UserAccountPersistenceMapper {

    // MapStruct recognizes this domain behavior as a fluent setter; it is not persisted state.
    @Mapping(target = "recordSuccessfulAuthentication", ignore = true)
    UserAccount toDomain(UserAccountEntity entity);

    UserAccountEntity toEntity(UserAccount account);

    default UserId toUserId(UUID value) {
        return value == null ? null : new UserId(value);
    }

    default UUID toUuid(UserId id) {
        return id == null ? null : id.value();
    }

    default short toSmallint(int value) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Failure count cannot be represented as a PostgreSQL smallint");
        }
        return (short) value;
    }
}
