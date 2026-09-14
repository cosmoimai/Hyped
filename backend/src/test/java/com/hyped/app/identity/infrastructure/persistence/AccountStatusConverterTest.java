package com.hyped.app.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.hyped.app.identity.domain.AccountStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AccountStatusConverterTest {

    private final AccountStatusConverter converter = new AccountStatusConverter();

    @ParameterizedTest
    @CsvSource({
        "ACTIVE, active",
        "LOCKED, locked",
        "SUSPENDED, suspended",
        "COMPROMISED, compromised",
        "DELETION_PENDING, deletion_pending",
        "DELETED, deleted"
    })
    void convertsAllDatabaseStatuses(AccountStatus status, String databaseValue) {
        assertThat(converter.convertToDatabaseColumn(status)).isEqualTo(databaseValue);
        assertThat(converter.convertToEntityAttribute(databaseValue)).isEqualTo(status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "ACTIVE", "Active", "", " active", "active "})
    void rejectsUnknownDatabaseValues(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> converter.convertToEntityAttribute(value));
    }

    @Test
    void preservesNullForJpaNullHandling() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
