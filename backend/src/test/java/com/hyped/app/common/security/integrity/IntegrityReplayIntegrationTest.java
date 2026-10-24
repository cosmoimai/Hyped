package com.hyped.app.common.security.integrity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class IntegrityReplayIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void concurrentReplayClaimsHaveExactlyOneWinnerAcrossInstances() throws Exception {
        var first = new JdbcIntegrityReplayStore(jdbc);
        var second = new JdbcIntegrityReplayStore(jdbc);
        String token = UUID.randomUUID().toString();
        String request = UUID.randomUUID().toString();
        Instant now = Instant.now();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.<Callable<Boolean>>of(
                    () -> first.claim(token, request, now), () -> second.claim(token, request, now)));
            assertThat(results.stream().filter(result -> {
                try {
                    return result.get();
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            }).count()).isEqualTo(1);
        }
        assertThat(second.claim("another-token", request, now)).isFalse();
        assertThat(second.claim(token, "another-request", now)).isFalse();
    }

    @Test
    void expiredDigestsArePrunedAndRateWindowIsDistributed() {
        var store = new JdbcIntegrityReplayStore(jdbc);
        Instant now = Instant.now();
        String session = UUID.randomUUID().toString();
        for (int i = 0; i < 60; i++) {
            assertThat(store.allow(session, now)).isTrue();
        }
        assertThat(new JdbcIntegrityReplayStore(jdbc).allow(session, now)).isFalse();
        assertThat(store.allow(session, now.plusSeconds(60))).isTrue();
        String token = UUID.randomUUID().toString();
        assertThat(store.claim(token, token, now)).isTrue();
        assertThat(store.claim(token, token, now.plusSeconds(601))).isTrue();
    }
}
