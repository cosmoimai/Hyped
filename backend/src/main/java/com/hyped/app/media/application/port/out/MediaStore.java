package com.hyped.app.media.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MediaStore {
    record Asset(
            UUID id,
            UUID owner,
            String type,
            int size,
            int width,
            int height,
            String sha,
            String status,
            UUID claim,
            Instant created,
            Instant expires,
            int attempts) {
        @Override
        public String toString() {
            return "MediaAsset[REDACTED]";
        }
    }

    boolean active(UUID owner);

    boolean inFlight(UUID owner, Instant since);

    int recent(UUID owner, Instant since);

    void insert(Asset asset, Instant now);

    Asset owned(UUID id, UUID owner);

    Asset find(UUID id);

    Asset claim(UUID id, UUID owner, UUID claim, Instant now);

    void finish(UUID id, UUID claim, String state, Instant now);

    boolean readable(UUID id, UUID actor);

    boolean readyForAttachment(UUID id, UUID owner);

    boolean markUnused(UUID id, UUID owner, Instant now);

    List<UUID> expiredUnused(Instant now);

    List<UUID> userAssets(UUID owner);

    void retireUser(UUID owner, Instant now);

    void removed(UUID id);
}
