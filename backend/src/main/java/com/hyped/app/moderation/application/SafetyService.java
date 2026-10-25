package com.hyped.app.moderation.application;

import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.moderation.application.port.out.SafetyStore;
import com.hyped.app.room.application.port.out.RoomRepository;
import com.hyped.app.room.domain.RoomId;
import com.hyped.app.room.domain.RoomStatus;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SafetyService {
    private final SafetyStore store;
    private final RoomRepository rooms;
    private final ObjectProvider<PersonalDataCipher> ciphers;
    private final Clock clock;
    private final ObjectProvider<
                    com.hyped.app.identity.application.service.AuthenticationProfileReader>
            profiles;
    private final TransactionTemplate tx;

    public SafetyService(
            SafetyStore store,
            RoomRepository rooms,
            ObjectProvider<PersonalDataCipher> ciphers,
            Clock clock,
            PlatformTransactionManager transactions,
            ObjectProvider<com.hyped.app.identity.application.service.AuthenticationProfileReader>
                    profiles) {
        this.profiles = profiles;
        this.store = store;
        this.rooms = rooms;
        this.ciphers = ciphers;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    public record ReportRequest(
            UUID roomId,
            String subjectType,
            UUID subjectUserId,
            String reasonCode,
            String details) {
        @Override
        public String toString() {
            return "ReportRequest[REDACTED]";
        }
    }

    public record Receipt(UUID reportId, String status) {}

    public record BlockResult(int removedCount, int leftCount) {}

    public Receipt report(UserId actor, String key, ReportRequest request) {
        UUID requestKey = key(key);
        validate(request);
        byte[] digest =
                digest(
                        request.roomId()
                                + "\n"
                                + request.subjectType()
                                + "\n"
                                + request.subjectUserId()
                                + "\n"
                                + request.reasonCode()
                                + "\n"
                                + request.details());
        // Encrypt outside the transaction: the production key registry is an external service.
        byte[] encrypted = null;
        if (request.details() != null && !request.details().isBlank()) {
            if (!store.active(actor.value())) {
                throw unavailable();
            }
            var reference = store.keyReference(actor.value());
            if (reference == null || ciphers.getIfAvailable() == null) {
                throw problem(503, "SAFETY_UNAVAILABLE", "Reporting is temporarily unavailable.");
            }
            byte[] plain = request.details().getBytes(StandardCharsets.UTF_8);
            try {
                encrypted =
                        ciphers.getObject()
                                .encrypt(actor, reference, PersonalDataField.REPORT_DETAILS, plain);
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        }
        byte[] ciphertext = encrypted;
        return tx.execute(
                status -> {
                    rooms.lockUser(actor);
                    if (!store.active(actor.value())) {
                        throw unavailable();
                    }
                    var replay = store.replay(actor.value(), requestKey, "REPORT");
                    if (replay != null) {
                        same(replay, digest);
                        return new Receipt(replay.resource(), "OPEN");
                    }
                    var room =
                            rooms.findAuthorizedForUpdate(new RoomId(request.roomId()), actor)
                                    .orElseThrow(SafetyService::unavailable)
                                    .room();
                    if (room.status() == RoomStatus.DELETING) {
                        throw unavailable();
                    }
                    if (request.subjectType().equals("ACCOUNT")
                            && (actor.value().equals(request.subjectUserId())
                                    || !store.linked(room.id().value(), request.subjectUserId()))) {
                        throw unavailable();
                    }
                    if (store.recentReports(actor.value(), clock.instant().minusSeconds(86400))
                            >= 5) {
                        throw problem(
                                429,
                                "REPORT_LIMIT_REACHED",
                                "You can submit up to five reports per day.");
                    }
                    Instant now = clock.instant();
                    UUID id = UUID.randomUUID();
                    UUID media =
                            request.subjectType().equals("THEME")
                                    ? room.theme().mediaAssetId()
                                    : null;
                    UUID subject =
                            request.subjectType().equals("ACCOUNT")
                                    ? request.subjectUserId()
                                    : null;
                    var report =
                            new SafetyStore.Report(
                                    id,
                                    room.id().value(),
                                    actor.value(),
                                    subject,
                                    media,
                                    request.subjectType(),
                                    request.reasonCode(),
                                    ciphertext,
                                    null,
                                    "OPEN",
                                    null,
                                    now,
                                    now.plusSeconds(90L * 86400));
                    store.insertReport(
                            report,
                            digest(
                                    request.subjectType()
                                            + ":"
                                            + (subject != null
                                                    ? subject
                                                    : media != null ? media : room.id().value())),
                            now);
                    store.remember(actor.value(), requestKey, "REPORT", digest, id, 0, 0, now);
                    return new Receipt(id, "OPEN");
                });
    }

    private static void validate(ReportRequest r) {
        if (r == null
                || r.roomId() == null
                || r.subjectType() == null
                || r.reasonCode() == null
                || !Set.of("ROOM", "THEME", "ACCOUNT").contains(r.subjectType())
                || !Set.of(
                                "INAPPROPRIATE_CONTENT",
                                "HARASSMENT",
                                "HATE_OR_VIOLENCE",
                                "SPAM",
                                "OTHER")
                        .contains(r.reasonCode())
                || (r.subjectType().equals("ACCOUNT") != (r.subjectUserId() != null))
                || (r.details() != null
                        && (r.details().length() > 500 || r.details().indexOf('\u0000') >= 0))) {
            throw problem(422, "VALIDATION_FAILED", "Check the report category and details.");
        }
    }

    public BlockResult block(UserId actor, UUID target, UUID context, String key) {
        UUID requestKey = key(key);
        if (actor.value().equals(target) || context == null) {
            throw problem(422, "VALIDATION_FAILED", "Select another account from a room.");
        }
        byte[] digest = digest(target + "\n" + context);
        return tx.execute(
                status -> {
                    lockPair(actor.value(), target);
                    if (!store.active(actor.value())) {
                        throw unavailable();
                    }
                    var replay = store.replay(actor.value(), requestKey, "BLOCK");
                    if (replay != null) {
                        same(replay, digest);
                        return new BlockResult(replay.removed(), replay.left());
                    }
                    int removed = 0;
                    int left = 0;
                    if (!store.ownBlock(actor.value(), target)) {
                        if (!store.active(target)) {
                            throw unavailable();
                        }
                        // Lock every affected room in stable order. Recheck the context after lock
                        // waits.
                        var shared = store.sharedRooms(actor.value(), target);
                        var locked =
                                shared.stream()
                                        .map(id -> rooms.findForUpdate(new RoomId(id)).orElse(null))
                                        .filter(java.util.Objects::nonNull)
                                        .toList();
                        if (locked.stream().noneMatch(r -> r.id().value().equals(context))
                                || !store.linked(context, actor.value())
                                || !store.linked(context, target)) {
                            throw unavailable();
                        }
                        Instant now = clock.instant();
                        store.insertBlock(actor.value(), target, now);
                        for (var room : locked) {
                            if (!store.linked(room.id().value(), actor.value())
                                    || !store.linked(room.id().value(), target)) {
                                continue;
                            }
                            if (room.ownerUserId().equals(actor)) {
                                rooms.removeMembership(room.id(), new UserId(target), now);
                                removed++;
                            } else if (room.ownerUserId().value().equals(target)) {
                                rooms.removeMembership(room.id(), actor, now);
                                left++;
                            } else {
                                rooms.incrementRevision(room.id(), now);
                            }
                            store.invalidate(actor.value(), room.id().value(), now);
                            store.invalidate(target, room.id().value(), now);
                        }
                    }
                    store.remember(
                            actor.value(),
                            requestKey,
                            "BLOCK",
                            digest,
                            target,
                            removed,
                            left,
                            clock.instant());
                    return new BlockResult(removed, left);
                });
    }

    public void unblock(UserId actor, UUID target) {
        if (actor.value().equals(target)) {
            throw unavailable();
        }
        tx.executeWithoutResult(
                status -> {
                    lockPair(actor.value(), target);
                    // Invalidate retained third-party snapshots before unmasking. Never recreate a
                    // membership.
                    if (store.ownBlock(actor.value(), target)) {
                        for (UUID id : store.sharedRooms(actor.value(), target)) {
                            rooms.findForUpdate(new RoomId(id));
                            rooms.incrementRevision(new RoomId(id), clock.instant());
                            store.invalidate(actor.value(), id, clock.instant());
                            store.invalidate(target, id, clock.instant());
                        }
                        store.unblock(actor.value(), target);
                    }
                });
    }

    private void lockPair(UUID first, UUID second) {
        List.of(first, second).stream()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(id -> rooms.lockUser(new UserId(id)));
    }

    public record BlockView(UUID userId, String displayName, Instant blockedAt) {}

    public record BlockPage(List<BlockView> items, UUID nextCursor) {}

    public BlockPage blocks(UserId actor, UUID after) {
        var rows = store.blocks(actor.value(), after);
        var items =
                rows.stream()
                        .limit(100)
                        .map(
                                block -> {
                                    String name = "Blocked account";
                                    if (store.active(block.userId())
                                            && profiles.getIfAvailable() != null) {
                                        try {
                                            name =
                                                    profiles.getObject()
                                                            .read(new UserId(block.userId()))
                                                            .displayName();
                                        } catch (RuntimeException ignored) {
                                            /* A deleted/unavailable profile remains safely unnamed. */
                                        }
                                    }
                                    return new BlockView(block.userId(), name, block.createdAt());
                                })
                        .toList();
        return new BlockPage(items, rows.size() > 100 ? items.getLast().userId() : null);
    }

    public List<SafetyStore.Invalidation> invalidations(UserId actor) {
        return store.invalidations(actor.value());
    }

    public void acknowledge(UserId actor, UUID version) {
        store.acknowledge(actor.value(), version);
    }

    public void cleanup() {
        store.cleanup(clock.instant());
    }

    private static void same(SafetyStore.Replay replay, byte[] digest) {
        if (!MessageDigest.isEqual(replay.digest(), digest)) {
            throw problem(
                    409,
                    "IDEMPOTENCY_KEY_REUSED",
                    "Retry with the original request or start a new action.");
        }
    }

    private static UUID key(String key) {
        try {
            UUID id = UUID.fromString(key);
            if (!id.toString().equalsIgnoreCase(key)) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw problem(400, "IDEMPOTENCY_KEY_REQUIRED", "A valid request key is required.");
        }
    }

    public static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Digest unavailable");
        }
    }

    public static SafetyException unavailable() {
        return problem(404, "ROOM_UNAVAILABLE", "This room or account is unavailable.");
    }

    public static SafetyException problem(int status, String code, String detail) {
        return new SafetyException(status, code, detail);
    }
}
