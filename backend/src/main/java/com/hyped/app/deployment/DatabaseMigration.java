package com.hyped.app.deployment;

import java.io.PrintStream;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogCreator;

/** One-off migration process: no Spring context, HTTP listener, or cloud credentials. */
public final class DatabaseMigration {
    private DatabaseMigration() {
    }

    public static void main(String[] args) {
        System.exit(run(System.getenv(), System.out, System.err));
    }

    static int run(Map<String, String> environment, PrintStream output, PrintStream error) {
        try {
            String url = required(environment, "DATABASE_URL");
            String username = required(environment, "DATABASE_USERNAME");
            String password = required(environment, "DATABASE_PASSWORD");
            if (!url.startsWith("jdbc:postgresql://")) {
                throw new IllegalArgumentException();
            }
            Flyway flyway = Flyway.configure()
                    .dataSource(url, username, password)
                    .locations("classpath:db/migration")
                    .defaultSchema("flyway")
                    .schemas("flyway", "app", "ops", "analytics")
                    .createSchemas(true)
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .baselineOnMigrate(false)
                    .loggers(SilentMigrationLog.class.getName())
                    .load();
            flyway.migrate();
            flyway.validate();
            output.println("Database migration and validation completed.");
            return 0;
        } catch (RuntimeException exception) {
            // Driver/Flyway exception messages can contain URLs, SQL, and credentials.
            error.println("Database migration failed. Check private configuration and database state.");
            return 1;
        }
    }

    private static String required(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException();
        }
        return value;
    }

    /** Flyway instantiates this logger; raw SQL and connection diagnostics stay suppressed. */
    public static final class SilentMigrationLog implements LogCreator, Log {
        @Override
        public Log createLogger(Class<?> type) {
            return this;
        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(String message) {
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Exception exception) {
        }

        @Override
        public void notice(String message) {
        }
    }
}
