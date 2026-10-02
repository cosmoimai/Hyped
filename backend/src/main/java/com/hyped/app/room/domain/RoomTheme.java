package com.hyped.app.room.domain;

import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record RoomTheme(
        RoomId roomId,
        Kind kind,
        String presetKey,
        String overlayKey,
        UserId updatedByUserId,
        Instant createdAt,
        Instant updatedAt) {
    private static final Set<String> PRESET_KEYS = Set.of("soft-blue-01", "sunset-coral-01", "mint-sky-01");
    private static final Set<String> GRADIENT_KEYS = Set.of("blue-lilac-02", "peach-gold-01", "aurora-green-01");
    private static final Set<String> OVERLAY_KEYS = Set.of("dark-soft", "light-soft", "none");

    public RoomTheme {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(kind, "kind");
        presetKey = boundedRequired(presetKey, 64, "presetKey");
        overlayKey = boundedRequired(overlayKey, 64, "overlayKey");
        Objects.requireNonNull(updatedByUserId, "updatedByUserId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
        boolean validPreset = kind == Kind.PRESET && PRESET_KEYS.contains(presetKey);
        boolean validGradient = kind == Kind.GRADIENT && GRADIENT_KEYS.contains(presetKey);
        if ((!validPreset && !validGradient) || !OVERLAY_KEYS.contains(overlayKey)) {
            throw new IllegalArgumentException("Unsupported room theme");
        }
    }

    public enum Kind {
        PRESET,
        GRADIENT
    }

    public static RoomTheme defaultTheme(RoomId roomId, UserId actor, Instant now) {
        return new RoomTheme(roomId, Kind.PRESET, "soft-blue-01", "dark-soft", actor, now, now);
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
}
