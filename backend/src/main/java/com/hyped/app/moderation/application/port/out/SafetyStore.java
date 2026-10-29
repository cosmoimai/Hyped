package com.hyped.app.moderation.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SafetyStore {
    record Replay(byte[] digest, UUID resource, int removed, int left) {}

    record Block(UUID userId, Instant createdAt) {}

    record Invalidation(UUID roomId, UUID version) {}

    record Report(
            UUID id,
            UUID roomId,
            UUID reporter,
            UUID subjectUser,
            UUID mediaId,
            String subjectType,
            String reason,
            byte[] details,
            String keyReference,
            String status,
            String resolution,
            Instant createdAt,
            Instant retainUntil) {
        @Override
        public String toString() {
            return "Report[REDACTED]";
        }
    }

    boolean active(UUID id);

    String keyReference(UUID id);

    boolean linked(UUID room, UUID user);

    boolean shared(UUID actor, UUID target);

    boolean blocked(UUID first, UUID second);

    Set<UUID> hidden(UUID actor, UUID room);

    boolean ownerConflict(UUID room, UUID owner);

    Replay replay(UUID actor, UUID key, String operation);

    void remember(
            UUID actor,
            UUID key,
            String operation,
            byte[] digest,
            UUID resource,
            int removed,
            int left,
            Instant now);

    int recentReports(UUID actor, Instant since);

    void insertReport(Report report, byte[] fingerprint, Instant now);

    List<Block> blocks(UUID actor, UUID after);

    boolean ownBlock(UUID actor, UUID target);

    void insertBlock(UUID actor, UUID target, Instant now);

    void unblock(UUID actor, UUID target);

    List<UUID> sharedRooms(UUID actor, UUID target);

    void invalidate(UUID actor, UUID room, Instant now);

    List<Invalidation> invalidations(UUID actor);

    void acknowledge(UUID actor, UUID version);

    List<Report> queue(int limit, Instant now);

    Report report(UUID id, boolean lock);

    void resolve(UUID id, String action, byte[] reviewer, Instant now);

    List<UUID> photoRooms(UUID media);

    List<UUID> lockPhoto(UUID media);

    boolean held(UUID room);

    void hold(UUID room, boolean held);

    void hidePhoto(UUID media, Instant now);

    void suspend(UUID user, Instant now);

    boolean allowReview(byte[] reviewer, Instant now);

    void cleanup(Instant now);
}
