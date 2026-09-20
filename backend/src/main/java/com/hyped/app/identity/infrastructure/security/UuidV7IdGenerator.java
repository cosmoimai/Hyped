package com.hyped.app.identity.infrastructure.security;

import com.hyped.app.identity.application.port.out.IdGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class UuidV7IdGenerator implements IdGenerator {

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private long lastMillis = -1;
    private int sequence;

    UuidV7IdGenerator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized UUID generate() {
        long millis = Math.max(clock.millis(), lastMillis);
        if (millis > lastMillis) {
            sequence = random.nextInt(4096);
        } else if (++sequence > 4095) {
            millis++;
            sequence = 0;
        }
        lastMillis = millis;
        long mostSignificant = (millis << 16) | 0x7000L | sequence;
        long leastSignificant = (random.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
