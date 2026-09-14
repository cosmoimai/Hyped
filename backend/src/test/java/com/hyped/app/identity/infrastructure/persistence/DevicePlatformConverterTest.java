package com.hyped.app.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.hyped.app.identity.domain.DevicePlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DevicePlatformConverterTest {

    private final DevicePlatformConverter converter = new DevicePlatformConverter();

    @ParameterizedTest
    @CsvSource({"ANDROID, android", "IOS, ios"})
    void convertsPlatforms(DevicePlatform platform, String value) {
        assertThat(converter.convertToDatabaseColumn(platform)).isEqualTo(value);
        assertThat(converter.convertToEntityAttribute(value)).isEqualTo(platform);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "ANDROID", "IOS", "", " android"})
    void rejectsUnknownValues(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> converter.convertToEntityAttribute(value));
    }

    @Test
    void preservesNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
