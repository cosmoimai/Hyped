package com.hyped.app.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.hyped.app.identity.domain.IdentityProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class IdentityProviderConverterTest {

    private final IdentityProviderConverter converter = new IdentityProviderConverter();

    @ParameterizedTest
    @CsvSource({"GOOGLE, google", "APPLE, apple"})
    void mapsLowercaseProviders(IdentityProvider provider, String value) {
        assertThat(converter.convertToDatabaseColumn(provider)).isEqualTo(value);
        assertThat(converter.convertToEntityAttribute(value)).isEqualTo(provider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "GOOGLE", "APPLE", "Google", "", " google", "apple "})
    void rejectsUnknownDatabaseValues(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> converter.convertToEntityAttribute(value));
    }

    @Test
    void preservesNullForJpaHandling() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
