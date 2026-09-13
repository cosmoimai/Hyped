package com.hyped.app.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SystemHealthControllerTest {

    @Test
    void returnsStableHealthResponseUsingInjectedClock() {
        Instant now = Instant.parse("2026-09-14T10:15:30Z");
        SystemHealthController controller =
                new SystemHealthController(Clock.fixed(now, ZoneOffset.UTC));

        var response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .isEqualTo(new SystemHealthController.SystemHealthResponse("UP", now));
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }
}

