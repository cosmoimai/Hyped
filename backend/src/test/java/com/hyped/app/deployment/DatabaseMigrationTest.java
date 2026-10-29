package com.hyped.app.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DatabaseMigrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD"})
    void missingCredentialsFailWithoutDefaultsOrSensitiveDiagnostics(String missing) {
        Map<String, String> environment = new HashMap<>(Map.of(
                "DATABASE_URL", "jdbc:postgresql://private-host.invalid/private-database",
                "DATABASE_USERNAME", "private-role", "DATABASE_PASSWORD", "private-password"));
        environment.remove(missing);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        assertThat(DatabaseMigration.run(environment, new PrintStream(output), new PrintStream(error))).isEqualTo(1);
        assertThat(output.size()).isZero();
        assertThat(error.toString(StandardCharsets.UTF_8)).isEqualTo(
                "Database migration failed. Check private configuration and database state.\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "jdbc:h2:mem:private-database", "postgresql://private:secret@host/database"})
    void rejectsEmptyAndNonPostgresUrlsWithoutEchoingInput(String url) {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        assertThat(DatabaseMigration.run(Map.of("DATABASE_URL", url,
                "DATABASE_USERNAME", "private-role", "DATABASE_PASSWORD", "private-password"),
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(error))).isEqualTo(1);
        assertThat(error.toString(StandardCharsets.UTF_8)).isEqualTo(
                "Database migration failed. Check private configuration and database state.\n");
    }
}
