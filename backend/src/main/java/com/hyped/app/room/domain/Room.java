package com.hyped.app.room.domain;

import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public record Room(
        RoomId id,
        UserId ownerUserId,
        String title,
        Instant eventAt,
        String eventTimeZone,
        String location,
        String description,
        RoomStatus status,
        long revision,
        int memberCount,
        Instant archivedAt,
        Instant deleteAfter,
        Instant createdAt,
        Instant updatedAt) {

    public Room {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerUserId, "ownerUserId");
        title = boundedRequired(title, 80, "title");
        Objects.requireNonNull(eventAt, "eventAt");
        eventTimeZone = boundedRequired(eventTimeZone, 64, "eventTimeZone");
        ZoneId.of(eventTimeZone);
        location = boundedOptional(location, 120, "location");
        description = boundedOptional(description, 500, "description");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (revision < 1 || memberCount < 1 || memberCount > 25) {
            throw new IllegalArgumentException("Invalid room revision or member count");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
        boolean archived = status != RoomStatus.ACTIVE;
        if (archived != (archivedAt != null && deleteAfter != null)) {
            throw new IllegalArgumentException("Archive timestamps must match room status");
        }
        if (archived && !deleteAfter.equals(archivedAt.plusSeconds(86_400))) {
            throw new IllegalArgumentException("deleteAfter must be 24 hours after archivedAt");
        }
    }

    private static String boundedRequired(String value, int maximum, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > maximum) {
            throw new IllegalArgumentException(field + " has invalid length");
        }
        return normalized;
    }

    private static String boundedOptional(String value, int maximum, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return boundedRequired(value, maximum, field);
    }
}
