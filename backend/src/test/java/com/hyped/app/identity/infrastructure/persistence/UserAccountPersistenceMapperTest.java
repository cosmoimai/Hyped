package com.hyped.app.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class UserAccountPersistenceMapperTest {

    private final UserAccountPersistenceMapper mapper = Mappers.getMapper(UserAccountPersistenceMapper.class);

    @Test
    void preservesLargestSmallintFailureCount() {
        UserAccount account = account(Short.MAX_VALUE);

        assertThat(mapper.toDomain(mapper.toEntity(account))).isEqualTo(account);
    }

    @Test
    void rejectsFailureCountThatWouldOverflowDatabaseType() {
        assertThatIllegalArgumentException().isThrownBy(() -> mapper.toEntity(account(Short.MAX_VALUE + 1)));
    }

    private static UserAccount account(int failures) {
        Instant createdAt = Instant.parse("2026-09-14T10:00:00Z");
        return new UserAccount(new UserId(UUID.randomUUID()), AccountStatus.ACTIVE, null, failures,
                null, null, null, null, createdAt, createdAt);
    }
}
