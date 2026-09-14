package com.hyped.app.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7IdGeneratorTest {

    @Test
    void generatesIncreasingVersionSevenIdsEvenWhenClockDoesNotAdvance() {
        Instant now = Instant.parse("2026-09-15T10:00:00Z");
        UuidV7IdGenerator generator = new UuidV7IdGenerator(Clock.fixed(now, ZoneOffset.UTC));
        UUID previous = generator.generate();
        assertThat(previous.getMostSignificantBits() >>> 16).isEqualTo(now.toEpochMilli());
        for (int index = 0; index < 5000; index++) {
            UUID current = generator.generate();
            assertThat(current.version()).isEqualTo(7);
            assertThat(current.variant()).isEqualTo(2);
            assertThat(current.compareTo(previous)).isPositive();
            previous = current;
        }
    }
}
