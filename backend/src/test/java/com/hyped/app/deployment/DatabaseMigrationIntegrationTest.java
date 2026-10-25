package com.hyped.app.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationIntegrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"postgres:17-alpine", "postgres:18-alpine"})
    void migratesAndValidatesBothSupportedVersionsAndCanBeRepeated(String image) throws Exception {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>(image)) {
            database.start();
            Map<String, String> environment = Map.of("DATABASE_URL", database.getJdbcUrl(),
                    "DATABASE_USERNAME", database.getUsername(), "DATABASE_PASSWORD", database.getPassword());
            ByteArrayOutputStream error = new ByteArrayOutputStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (int attempt = 0; attempt < 2; attempt++) {
                assertThat(DatabaseMigration.run(environment, new PrintStream(output), new PrintStream(error)))
                        .isZero();
            }
            assertThat(error.size()).isZero();
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(),
                    database.getUsername(), database.getPassword());
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery("""
                            SELECT count(*), max(version::integer)
                            FROM flyway.flyway_schema_history WHERE success AND version IS NOT NULL
                            """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(16);
                assertThat(rows.getInt(2)).isEqualTo(16);
            }

            ByteArrayOutputStream failure = new ByteArrayOutputStream();
            assertThat(DatabaseMigration.run(Map.of("DATABASE_URL", database.getJdbcUrl(),
                    "DATABASE_USERNAME", database.getUsername(), "DATABASE_PASSWORD", "invalid-private-password"),
                    new PrintStream(output), new PrintStream(failure))).isEqualTo(1);
            assertThat(failure.toString(StandardCharsets.UTF_8)).isEqualTo(
                    "Database migration failed. Check private configuration and database state.\n");
        }
    }
}
