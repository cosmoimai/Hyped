package com.hyped.app.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoomTest {
    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");

    @Test
    void trimsAndValidatesVisibleFields() {
        Room room = room("  Goa trip  ", "  Goa  ", "  Trip  ");

        assertThat(room.title()).isEqualTo("Goa trip");
        assertThat(room.location()).isEqualTo("Goa");
        assertThat(room.description()).isEqualTo("Trip");
        assertThatThrownBy(() -> room(" ", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> room("x".repeat(81), null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void archivedRoomRequiresExactDeletionDelay() {
        assertThatThrownBy(() -> new Room(new RoomId(UUID.randomUUID()), new UserId(UUID.randomUUID()),
                "Title", NOW.plusSeconds(60), "UTC", null, null, RoomStatus.ARCHIVED, 1, 1,
                NOW, NOW.plusSeconds(60), NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    private Room room(String title, String location, String description) {
        return new Room(new RoomId(UUID.randomUUID()), new UserId(UUID.randomUUID()), title,
                NOW.plusSeconds(60), "UTC", location, description, RoomStatus.ACTIVE, 1, 1,
                null, null, NOW, NOW);
    }
}
