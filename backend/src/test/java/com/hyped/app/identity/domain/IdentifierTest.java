package com.hyped.app.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IdentifierTest {

    @ParameterizedTest
    @MethodSource("identifiers")
    void rejectsNullUuid(Function<UUID, Object> factory) {
        assertThatNullPointerException().isThrownBy(() -> factory.apply(null));
    }

    @ParameterizedTest
    @MethodSource("identifiers")
    void preservesUuidAndUsesCanonicalString(Function<UUID, Object> factory) {
        UUID value = UUID.fromString("019b1f20-4152-7ce8-ae9e-b12b87fe8a31");

        Object identifier = factory.apply(value);

        assertThat(identifier).hasToString(value.toString()).isEqualTo(factory.apply(value));
    }

    private static Stream<Function<UUID, Object>> identifiers() {
        return Stream.of(UserId::new, IdentityId::new, DeviceId::new, SessionId::new,
                RefreshTokenId::new, TokenFamilyId::new, InstallationId::new);
    }
}
