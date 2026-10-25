package com.hyped.app.media.application;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.media.application.port.out.MediaObjects;
import com.hyped.app.media.application.port.out.MediaStore;
import com.hyped.app.media.application.port.out.PhotoModerator;
import com.hyped.app.media.application.port.out.PhotoProcessor;
import com.hyped.app.policy.application.PolicyService;
import com.hyped.app.room.application.port.out.RoomRepository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaService {
    private final MediaStore store;
    private final RoomRepository rooms;
    private final PolicyService policies;
    private final ObjectProvider<MediaObjects> objects;
    private final ObjectProvider<PhotoModerator> moderators;
    private final PhotoProcessor processor;
    private final Clock clock;
    private final TransactionTemplate tx;

    public MediaService(
            MediaStore store,
            RoomRepository rooms,
            PolicyService policies,
            ObjectProvider<MediaObjects> objects,
            ObjectProvider<PhotoModerator> moderators,
            PhotoProcessor processor,
            Clock clock,
            PlatformTransactionManager transactions) {
        this.store = store;
        this.rooms = rooms;
        this.policies = policies;
        this.objects = objects;
        this.moderators = moderators;
        this.processor = processor;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    public boolean available() {
        return objects.getIfAvailable() != null && moderators.getIfAvailable() != null;
    }

    private MediaObjects storage() {
        var result = objects.getIfAvailable();
        if (result == null || moderators.getIfAvailable() == null) {
            throw MediaException.unavailable();
        }
        return result;
    }

    public record Request(
            String scope,
            String fileName,
            String contentType,
            int sizeBytes,
            int widthPx,
            int heightPx,
            String contentSha256) {
        @Override
        public String toString() {
            return "MediaRequest[REDACTED]";
        }
    }

    public record Upload(
            String method,
            String url,
            Map<String, String> headers,
            Instant expiresAt,
            int maximumBytes) {
        @Override
        public String toString() {
            return "MediaUpload[REDACTED]";
        }
    }

    public record Authorized(UUID mediaAssetId, Upload upload) {
        @Override
        public String toString() {
            return "MediaAuthorization[REDACTED]";
        }
    }

    public record State(UUID mediaAssetId, String status) {}

    public record Delivery(UUID mediaAssetId, String url, Instant expiresAt) {
        @Override
        public String toString() {
            return "MediaDelivery[REDACTED]";
        }
    }

    public Authorized authorize(UserId actor, Request request) {
        MediaObjects cloud = storage();
        if (!"ROOM_COVER".equals(request.scope())
                || !Set.of("image/jpeg", "image/png", "image/webp")
                        .contains(request.contentType())) {
            throw new MediaException(415, "MEDIA_TYPE_UNSUPPORTED");
        }
        if (request.sizeBytes() < 1 || request.sizeBytes() > 1572864) {
            throw new MediaException(422, "MEDIA_TOO_LARGE");
        }
        if (request.widthPx() < 1
                || request.heightPx() < 1
                || request.widthPx() > 2048
                || request.heightPx() > 2048) {
            throw new MediaException(422, "MEDIA_DIMENSIONS_EXCEEDED");
        }
        hash(request.contentSha256());
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        tx.executeWithoutResult(
                status -> {
                    rooms.lockUser(actor);
                    if (!store.active(actor.value())) {
                        throw new MediaException(401, "SESSION_EXPIRED");
                    }
                    policies.requireCurrent(actor);
                    if (store.recent(actor.value(), now.minusSeconds(86400)) >= 40) {
                        throw new MediaException(429, "MEDIA_UPLOAD_LIMIT");
                    }
                    store.insert(
                            new MediaStore.Asset(
                                    id,
                                    actor.value(),
                                    request.contentType(),
                                    request.sizeBytes(),
                                    request.widthPx(),
                                    request.heightPx(),
                                    request.contentSha256(),
                                    "PENDING",
                                    null,
                                    now,
                                    now.plusSeconds(600),
                                    0),
                            now);
                });
        try {
            var upload = cloud.upload(id, request.contentType(), request.sizeBytes());
            return new Authorized(
                    id,
                    new Upload(
                            "PUT", upload.url(), upload.headers(), now.plusSeconds(600), 1572864));
        } catch (RuntimeException failure) {
            throw MediaException.unavailable();
        }
    }

    public State complete(UserId actor, UUID id, String sha) {
        MediaObjects cloud = storage();
        byte[] expected = hash(sha);
        UUID claim = UUID.randomUUID();
        MediaStore.Asset asset =
                tx.execute(
                        status -> {
                            rooms.lockUser(actor);
                            if (!store.active(actor.value())) {
                                throw new MediaException(401, "SESSION_EXPIRED");
                            }
                            policies.requireCurrent(actor);
                            var existing = store.owned(id, actor.value());
                            if (existing == null) {
                                throw new MediaException(404, "UPLOAD_NOT_FOUND");
                            }
                            if (!MessageDigest.isEqual(hash(existing.sha()), expected)) {
                                throw new MediaException(422, "MEDIA_HASH_MISMATCH");
                            }
                            if (Set.of("READY", "MANUAL_REVIEW", "REJECTED")
                                    .contains(existing.status())) {
                                return existing;
                            }
                            if (!existing.expires().isAfter(clock.instant())) {
                                throw new MediaException(410, "UPLOAD_EXPIRED");
                            }
                            var acquired = store.claim(id, actor.value(), claim, clock.instant());
                            return acquired == null ? existing : acquired;
                        });
        if (!claim.equals(asset.claim())) {
            return new State(id, asset.status());
        }
        try {
            byte[] raw = cloud.read(id, asset.type(), asset.size());
            byte[] actual = MessageDigest.getInstance("SHA-256").digest(raw);
            if (!MessageDigest.isEqual(actual, expected)) {
                throw new MediaException(422, "MEDIA_HASH_MISMATCH");
            }
            byte[] safe = processor.sanitize(raw, asset.type(), asset.width(), asset.height());
            var decision = moderators.getObject().evaluate(safe);
            String next = switch (decision) {
                case APPROVED -> "READY";
                case UNCERTAIN -> "MANUAL_REVIEW";
                case REJECTED -> "REJECTED";
            };
            if (decision == PhotoModerator.Decision.APPROVED) {
                cloud.publish(id, safe);
            }
            tx.executeWithoutResult(status -> store.finish(id, claim, next, clock.instant()));
            // Original upload key is never used for delivery; replayed PUTs cannot replace a
            // sanitized cover.
            var saved = store.owned(id, actor.value());
            if (decision == PhotoModerator.Decision.APPROVED
                    && (saved == null || !"READY".equals(saved.status()))) {
                cloud.delete(id);
            }
            return new State(id, saved == null ? "REJECTED" : saved.status());
        } catch (Exception failure) {
            boolean temporary = !(failure instanceof MediaException media) || media.status() >= 500;
            tx.executeWithoutResult(
                    status ->
                            store.finish(
                                    id,
                                    claim,
                                    temporary ? "PENDING" : "REJECTED",
                                    clock.instant()));
            if (failure instanceof MediaException media) {
                throw media;
            }
            throw MediaException.unavailable();
        }
    }

    public void requireReady(UserId owner, UUID id) {
        if (!store.readyForAttachment(id, owner.value())) {
            throw new MediaException(422, "MEDIA_NOT_READY");
        }
    }

    public Delivery delivery(UserId actor, UUID id) {
        MediaObjects cloud = storage();
        if (!store.readable(id, actor.value())) {
            throw new MediaException(404, "MEDIA_UNAVAILABLE");
        }
        try {
            String url = cloud.delivery(id);
            if (!store.readable(id, actor.value())) {
                throw new MediaException(404, "MEDIA_UNAVAILABLE");
            }
            return new Delivery(id, url, clock.instant().plusSeconds(900));
        } catch (MediaException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw MediaException.unavailable();
        }
    }

    public void delete(UserId actor, UUID id) {
        MediaObjects cloud = storage();
        boolean allowed =
                Boolean.TRUE.equals(
                        tx.execute(status -> store.markUnused(id, actor.value(), clock.instant())));
        if (!allowed) {
            throw new MediaException(404, "MEDIA_UNAVAILABLE");
        }
        remove(cloud, id);
    }

    private void remove(MediaObjects cloud, UUID id) {
        try {
            cloud.delete(id);
            store.removed(id);
        } catch (RuntimeException failure) {
            throw MediaException.unavailable();
        }
    }

    public void cleanup() {
        if (!available()) {
            return;
        }
        var expired = tx.execute(status -> store.expiredUnused(clock.instant()));
        for (UUID id : expired) {
            try {
                remove(storage(), id);
            } catch (MediaException ignored) {
                /* Durable retry. */
            }
        }
    }

    public void purgeUser(UUID user) {
        var ids = store.userAssets(user);
        if (ids.isEmpty()) {
            return;
        }
        MediaObjects cloud = storage();
        tx.executeWithoutResult(
                status -> {
                    if (store.inFlight(user, clock.instant().minusSeconds(600))) {
                        throw MediaException.unavailable();
                    }
                    store.retireUser(user, clock.instant());
                });
        ids.forEach(id -> remove(cloud, id));
    }

    private static byte[] hash(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length == 32 && value.length() == 44) {
                return bytes;
            }
        } catch (RuntimeException ignored) {
            /* No input in errors. */
        }
        throw new MediaException(422, "MEDIA_HASH_MISMATCH");
    }
}
