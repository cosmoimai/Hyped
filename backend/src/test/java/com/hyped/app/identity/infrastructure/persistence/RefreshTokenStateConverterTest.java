package com.hyped.app.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.hyped.app.identity.domain.RefreshTokenState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RefreshTokenStateConverterTest {

    private final RefreshTokenStateConverter converter = new RefreshTokenStateConverter();

    @ParameterizedTest
    @CsvSource({"ACTIVE, active", "CONSUMED, consumed", "REVOKED, revoked"})
    void mapsLowercaseValues(RefreshTokenState state, String value) {
        assertThat(converter.convertToDatabaseColumn(state)).isEqualTo(value);
        assertThat(converter.convertToEntityAttribute(value)).isEqualTo(state);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "unknown", "", " consumed"})
    void rejectsUnknownValues(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> converter.convertToEntityAttribute(value));
    }

    @Test
    void preservesNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
