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
        RoomId roomId = new RoomId(UUID.randomUUID());
        UserId owner = new UserId(UUID.randomUUID());
        assertThatThrownBy(() -> new Room(roomId, owner, "Title", NOW.plusSeconds(60), "UTC", null, null,
                RoomTheme.defaultTheme(roomId, owner, NOW),
                RoomStatus.ARCHIVED, 1, 1, NOW, NOW.plusSeconds(60), NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Room room(String title, String location, String description) {
        RoomId roomId = new RoomId(UUID.randomUUID());
        UserId owner = new UserId(UUID.randomUUID());
        return new Room(roomId, owner, title, NOW.plusSeconds(60), "UTC", location, description,
                RoomTheme.defaultTheme(roomId, owner, NOW), RoomStatus.ACTIVE, 1, 1, null, null, NOW, NOW);
    }
}
