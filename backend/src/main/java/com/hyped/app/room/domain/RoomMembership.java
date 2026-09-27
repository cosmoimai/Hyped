package com.hyped.app.room.domain;

import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.Objects;

public record RoomMembership(
        RoomId roomId,
        UserId userId,
        MembershipRole role,
        Instant joinedAt,
        Instant updatedAt) {
    public RoomMembership {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(joinedAt, "joinedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(joinedAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before joinedAt");
        }
    }
}
