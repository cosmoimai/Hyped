package com.hyped.app.moderation.application;

import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.moderation.application.port.out.SafetyStore;
import com.hyped.app.reminder.application.RoomReminderService;
import com.hyped.app.room.application.port.out.RoomRepository;
import com.hyped.app.room.domain.RoomId;
import com.hyped.app.room.domain.RoomStatus;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ModerationService {
    private final SafetyStore store;
    private final RoomRepository rooms;
    private final ObjectProvider<PersonalDataCipher> ciphers;
    private final ObjectProvider<RoomReminderService> reminders;
    private final Clock clock;
    private final ObjectProvider<com.hyped.app.media.application.MediaService> media;
    private final TransactionTemplate tx;

    public ModerationService(
            SafetyStore store,
            RoomRepository rooms,
            ObjectProvider<PersonalDataCipher> ciphers,
            ObjectProvider<RoomReminderService> reminders,
            Clock clock,
            PlatformTransactionManager transactions,
            ObjectProvider<com.hyped.app.media.application.MediaService> media) {
        this.media = media;
        this.store = store;
        this.rooms = rooms;
        this.ciphers = ciphers;
        this.reminders = reminders;
        this.clock = clock;
        tx = new TransactionTemplate(transactions);
    }

    public record QueueItem(
            UUID reportId,
            UUID roomId,
            String subjectType,
            String reasonCode,
            String details,
            String status,
            Instant createdAt,
            Instant retainUntil) {
        @Override
        public String toString() {
            return "ModerationQueueItem[REDACTED]";
        }
    }

    public void requireRate(String reviewer) {
        if (!store.allowReview(SafetyService.digest(reviewer), clock.instant())) {
            throw SafetyService.problem(
                    429, "MODERATION_RATE_LIMITED", "Too many review requests. Try again shortly.");
        }
    }

    public List<QueueItem> queue(int limit) {
        if (limit < 1 || limit > 100) {
            throw SafetyService.problem(422, "VALIDATION_FAILED", "Use a limit from 1 to 100.");
        }
        return store.queue(limit, clock.instant()).stream()
                .map(
                        report ->
                                new QueueItem(
                                        report.id(),
                                        report.roomId(),
                                        report.subjectType(),
                                        report.reason(),
                                        details(report),
                                        report.status(),
                                        report.createdAt(),
                                        report.retainUntil()))
                .toList();
    }

    private String details(SafetyStore.Report r) {
        if (r.reporter() == null
                || r.details() == null
                || r.keyReference() == null
                || !store.active(r.reporter())) {
            return null;
        }
        byte[] plain = null;
        try {
            plain =
                    ciphers.getObject()
                            .decrypt(
                                    new UserId(r.reporter()),
                                    r.keyReference(),
                                    PersonalDataField.REPORT_DETAILS,
                                    r.details());
            // Deletion may have started while resolving an external key.
            return store.active(r.reporter()) ? new String(plain, StandardCharsets.UTF_8) : null;
        } catch (RuntimeException failure) {
            return null;
        } finally {
            if (plain != null) {
                Arrays.fill(plain, (byte) 0);
            }
        }
    }

    public record Context(
            UUID reportId,
            String roomTitle,
            String description,
            Long roomRevision,
            UUID photoId,
            UUID subjectUserId) {
        @Override
        public String toString() {
            return "ModerationContext[REDACTED]";
        }
    }

    public Context context(UUID id) {
        var report = store.report(id, false);
        if (report == null || !report.retainUntil().isAfter(clock.instant())) {
            throw SafetyService.unavailable();
        }
        var room =
                report.roomId() == null
                        ? null
                        : tx.execute(
                                status ->
                                        rooms.findForUpdate(new RoomId(report.roomId()))
                                                .orElse(null));
        return new Context(
                id,
                room == null ? null : room.title(),
                room == null ? null : room.description(),
                room == null ? null : room.revision(),
                report.mediaId(),
                report.subjectUser());
    }

    public byte[] photo(UUID id) {
        var report = store.report(id, false);
        if (report == null
                || report.mediaId() == null
                || !report.retainUntil().isAfter(clock.instant())) {
            throw SafetyService.unavailable();
        }
        return media.getObject().reviewPhoto(report.mediaId());
    }

    public void decide(UUID id, String action, Long expectedRevision, String reviewer) {
        if (action == null
                || !Set.of(
                                "DISMISS",
                                "REMOVE_PHOTO",
                                "ARCHIVE_ROOM",
                                "RELEASE_ROOM",
                                "SUSPEND_ACCOUNT")
                        .contains(action)) {
            throw SafetyService.problem(
                    422, "VALIDATION_FAILED", "Select an approved moderation action.");
        }
        // Acquire account -> sorted rooms -> report -> asset. Never start by locking the report.
        var preview = store.report(id, false);
        if (preview == null) {
            throw SafetyService.unavailable();
        }
        tx.executeWithoutResult(
                status -> {
                    if (action.equals("SUSPEND_ACCOUNT") && preview.subjectUser() != null) {
                        rooms.lockUser(new UserId(preview.subjectUser()));
                    }
                    var photoRooms =
                            preview.mediaId() == null
                                    ? List.<UUID>of()
                                    : store.photoRooms(preview.mediaId());
                    var ids =
                            new java.util.TreeSet<UUID>(
                                    java.util.Comparator.comparing(UUID::toString));
                    if (preview.roomId() != null) {
                        ids.add(preview.roomId());
                    }
                    if (action.equals("REMOVE_PHOTO")) {
                        ids.addAll(photoRooms);
                    }
                    var locked = new java.util.HashMap<UUID, com.hyped.app.room.domain.Room>();
                    for (UUID room : ids) {
                        rooms.findForUpdate(new RoomId(room)).ifPresent(r -> locked.put(room, r));
                    }
                    var report = store.report(id, true);
                    if (report == null || !report.retainUntil().isAfter(clock.instant())) {
                        throw SafetyService.unavailable();
                    }
                    if (!report.status().equals("OPEN")
                            && !(action.equals("RELEASE_ROOM")
                                    && "ARCHIVE_ROOM".equals(report.resolution())
                                    && report.status().equals("ACTIONED"))) {
                        if (action.equals(report.resolution())) {
                            return;
                        }
                        throw SafetyService.problem(
                                409,
                                "REPORT_ALREADY_RESOLVED",
                                "This report has already been reviewed.");
                    }
                    Instant now = clock.instant();
                    if (action.equals("ARCHIVE_ROOM")) {
                        var room = locked.get(report.roomId());
                        if (room == null) {
                            throw SafetyService.unavailable();
                        }
                        if (expectedRevision == null || room.revision() != expectedRevision) {
                            throw SafetyService.problem(
                                    412,
                                    "ROOM_REVISION_MISMATCH",
                                    "Reload the report context before acting.");
                        }
                        if (room.status() == RoomStatus.ACTIVE) {
                            rooms.archive(room.id(), now, now.plusSeconds(86400), now);
                            reminders.ifAvailable(r -> r.cancelOpen(room.id()));
                            rooms.cancelDeliveries(room.id(), now);
                        }
                        store.hold(room.id().value(), true);
                        invalidateMembers(room.id(), now);
                    } else if (action.equals("REMOVE_PHOTO")) {
                        if (report.mediaId() == null) {
                            throw SafetyService.problem(
                                    409,
                                    "REPORT_SUBJECT_UNAVAILABLE",
                                    "This report has no uploaded photo.");
                        }
                        // A cover cannot be attached to additional rooms during this action: asset
                        // lock below
                        // serializes attachment, and the second reference snapshot detects a
                        // changed room set.
                        for (UUID room : store.lockPhoto(report.mediaId())) {
                            if (!ids.contains(room)) {
                                throw SafetyService.problem(
                                        409, "REPORT_CHANGED", "Reload and retry.");
                            }
                        }
                        store.hidePhoto(report.mediaId(), now);
                        for (UUID room : photoRooms) {
                            invalidateMembers(new RoomId(room), now);
                        }
                    } else if (action.equals("RELEASE_ROOM")) {
                        if (!"ARCHIVE_ROOM".equals(report.resolution())
                                || !locked.containsKey(report.roomId())) {
                            throw SafetyService.problem(
                                    409,
                                    "REPORT_SUBJECT_UNAVAILABLE",
                                    "The archived room is unavailable.");
                        }
                        store.hold(report.roomId(), false);
                        rooms.incrementRevision(new RoomId(report.roomId()), now);
                        invalidateMembers(new RoomId(report.roomId()), now);
                    } else if (action.equals("SUSPEND_ACCOUNT")) {
                        if (report.subjectUser() == null
                                || !report.subjectType().equals("ACCOUNT")) {
                            throw SafetyService.problem(
                                    422, "VALIDATION_FAILED", "This is not an account report.");
                        }
                        store.suspend(report.subjectUser(), now);
                    }
                    store.resolve(id, action, SafetyService.digest(reviewer), now);
                });
    }

    private void invalidateMembers(RoomId room, Instant now) {
        for (var member : rooms.findMembers(room)) {
            store.invalidate(member.userId().value(), room.value(), now);
        }
    }
}
