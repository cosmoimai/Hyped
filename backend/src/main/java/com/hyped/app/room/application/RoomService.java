package com.hyped.app.room.application;

import com.hyped.app.identity.application.service.AuthenticationProfileReader;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.invitation.application.InvitationService;
import com.hyped.app.reminder.application.ReminderPreferencesService;
import com.hyped.app.reminder.application.RoomReminderService;
import com.hyped.app.room.application.port.out.RoomRepository;
import com.hyped.app.room.application.port.out.RoomRepository.AuthorizedRoom;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.Room;
import com.hyped.app.room.domain.RoomId;
import com.hyped.app.room.domain.RoomMembership;
import com.hyped.app.room.domain.RoomStatus;
import com.hyped.app.room.domain.RoomTheme;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class RoomService {
    public static final int MAX_OWNED_ROOMS = 10;
    public static final int MAX_JOINED_ROOMS = 25;
    public static final int MAX_MEMBERS = 25;
    private final com.hyped.app.policy.application.PolicyService policies;
    private final RoomRepository rooms;
    private final RoomMemberSnapshotService memberSnapshots;
    private final ObjectProvider<AuthenticationProfileReader> profiles;
    private final Clock clock;
    private final ObjectProvider<InvitationService> invitations;
    private final ObjectProvider<RoomReminderService> reminders;
    private final ObjectProvider<ReminderPreferencesService> reminderPreferences;
    private final ObjectProvider<com.hyped.app.media.application.MediaService> media;

    public RoomService(
            RoomRepository rooms,
            Clock clock,
            ObjectProvider<InvitationService> invitations,
            ObjectProvider<RoomReminderService> reminders,
            ObjectProvider<ReminderPreferencesService> reminderPreferences,
            ObjectProvider<AuthenticationProfileReader> profiles,
            com.hyped.app.policy.application.PolicyService policies,
            RoomMemberSnapshotService memberSnapshots,
            ObjectProvider<com.hyped.app.media.application.MediaService> media) {
        this.media = media;
        this.policies = policies;
        this.rooms = rooms;
        this.memberSnapshots = memberSnapshots;
        this.profiles = profiles;
        this.clock = clock;
        this.invitations = invitations;
        this.reminders = reminders;
        this.reminderPreferences = reminderPreferences;
    }

    @Transactional
    public AuthorizedRoom create(UserId actor, CreateRoomCommand command) {
        rooms.lockUser(actor);
        policies.requireCurrent(actor);
        if (rooms.countActiveOwned(actor) >= MAX_OWNED_ROOMS) {
            throw problem(
                    RoomOperationException.Kind.CONFLICT,
                    "OWNED_ROOM_LIMIT_REACHED",
                    "Owned room limit reached",
                    "This account already owns ten active rooms.");
        }
        Instant now = clock.instant();
        Instant eventAt = resolve(command.eventLocalDate(), command.eventLocalTime(), command.eventTimeZone());
        if (!eventAt.isAfter(now)) {
            throw validation("EVENT_TIME_NOT_FUTURE", "The event time must be in the future.");
        }
        RoomId roomId = new RoomId(UUID.randomUUID());
        RoomTheme theme = command.theme() == null ? null : theme(roomId, actor, command.theme(), now, now);
        Room room = room(roomId, actor, command.title(), eventAt,
                command.eventTimeZone(), command.location(), command.description(), RoomStatus.ACTIVE,
                1, 1, null, null, now, now, theme);
        RoomMembership owner = new RoomMembership(room.id(), actor, MembershipRole.OWNER, now, now);
        rooms.create(room, owner);
        reminderPreferences.ifAvailable(service -> service.createDefaults(room.id(), actor));
        reminders.ifAvailable(service -> service.replaceForEvent(room.id(), room.eventAt()));
        // Provision in this transaction when the invitation module is configured; failure rolls back the room.
        invitations.ifAvailable(service -> service.get(actor, room.id()));
        return new AuthorizedRoom(room, MembershipRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<AuthorizedRoom> list(UserId actor, RoomStatus status, int limit) {
        if (limit < 1 || limit > 50 || status == RoomStatus.DELETING) {
            throw validation("VALIDATION_FAILED", "The room-list filter is invalid.");
        }
        return rooms.findByUser(actor, status, limit);
    }

    @Transactional(readOnly = true)
    public AuthorizedRoom get(UserId actor, RoomId roomId) {
        AuthorizedRoom room = rooms.findAuthorized(roomId, actor).orElseThrow(RoomService::unavailable);
        if (room.room().status() == RoomStatus.DELETING) {
            throw unavailable();
        }
        return room;
    }

    @Transactional
    public AuthorizedRoom update(UserId actor, RoomId roomId, long expectedRevision, UpdateRoomCommand command) {
        AuthorizedRoom current = lockedActive(actor, roomId);
        requireEditor(current.role());
        if (current.room().revision() != expectedRevision) {
            throw revisionMismatch();
        }
        Room existing = current.room();
        Instant now = clock.instant();
        Instant eventAt = resolveUpdate(existing, command);
        if (!eventAt.isAfter(now)) {
            throw validation("EVENT_TIME_NOT_FUTURE", "The event time must be in the future.");
        }
        RoomTheme theme =
                command.themePresent()
                        ? theme(
                                existing.id(),
                                actor,
                                command.theme(),
                                existing.theme().createdAt(),
                                now,
                                existing.theme().mediaAssetId())
                        : new RoomTheme(
                                existing.id(),
                                existing.theme().kind(),
                                existing.theme().presetKey(),
                                existing.theme().mediaAssetId(),
                                existing.theme().overlayKey(),
                                existing.theme().updatedByUserId(),
                                existing.theme().createdAt(),
                                existing.theme().updatedAt());
        Room changed = room(existing.id(), existing.ownerUserId(),
                command.title() == null ? existing.title() : command.title(), eventAt,
                command.eventTimeZone() == null ? existing.eventTimeZone() : command.eventTimeZone(),
                command.locationPresent() ? command.location() : existing.location(),
                command.descriptionPresent() ? command.description() : existing.description(),
                existing.status(), existing.revision() + 1, existing.memberCount(), null, null,
                existing.createdAt(), now, theme);
        if (!rooms.updateVisibleFields(changed, expectedRevision)) {
            throw revisionMismatch();
        }
        if (!eventAt.equals(existing.eventAt())) {
            reminders.ifAvailable(service -> service.replaceForEvent(existing.id(), eventAt));
        }
        return new AuthorizedRoom(changed, current.role());
    }

    @Transactional
    public AuthorizedRoom archive(UserId actor, RoomId roomId) {
        return archive(actor, roomId, null);
    }

    @Transactional
    public AuthorizedRoom archive(UserId actor, RoomId roomId, Long expectedRevision) {
        AuthorizedRoom current = rooms.findAuthorizedForUpdate(roomId, actor).orElseThrow(RoomService::unavailable);
        if (current.role() != MembershipRole.OWNER) {
            throw forbidden();
        }
        if (current.room().status() == RoomStatus.ARCHIVED) {
            return current;
        }
        if (current.room().status() != RoomStatus.ACTIVE) {
            throw unavailable();
        }
        checkRevision(current, expectedRevision);
        Instant now = clock.instant();
        Instant deleteAfter = now.plus(Duration.ofDays(1));
        rooms.archive(roomId, now, deleteAfter, now);
        reminders.ifAvailable(service -> service.cancelOpen(roomId));
        rooms.cancelDeliveries(roomId, now);
        Room existing = current.room();
        Room archived = room(existing.id(), existing.ownerUserId(), existing.title(), existing.eventAt(),
                existing.eventTimeZone(), existing.location(), existing.description(), RoomStatus.ARCHIVED,
                existing.revision() + 1, existing.memberCount(), now, deleteAfter, existing.createdAt(), now,
                existing.theme());
        return new AuthorizedRoom(archived, current.role());
    }

    @Transactional
    public void leave(UserId actor, RoomId roomId, Long expectedRevision) {
        // Account -> room -> membership, shared with join and transfer.
        rooms.lockUser(actor);
        var room = rooms.findForUpdate(roomId);
        if (room.isEmpty()) {
            return;
        }
        var membership = rooms.findMembershipForUpdate(roomId, actor);
        if (membership.isEmpty()) {
            return; // Repeated leave and foreign IDs disclose no membership information.
        }
        if (membership.get().role() == MembershipRole.OWNER) {
            throw problem(RoomOperationException.Kind.CONFLICT, "OWNERSHIP_TRANSFER_REQUIRED",
                    "Transfer ownership first", "Transfer ownership before leaving this room.");
        }
        checkRevision(new AuthorizedRoom(room.get(), membership.get().role()), expectedRevision);
        rooms.removeMembership(roomId, actor, clock.instant());
    }

    @Transactional
    public AuthorizedRoom restore(UserId actor, RoomId roomId, Long expectedRevision) {
        // Snapshot the bounded member set before taking any room locks. Archive forbids joins/role changes.
        var preview = rooms.findAuthorized(roomId, actor).orElseThrow(RoomService::unavailable);
        if (preview.role() != MembershipRole.OWNER) {
            throw forbidden();
        }
        var lockedUsers = rooms.findMembers(roomId).stream().map(RoomMembership::userId)
                .sorted(Comparator.comparing(user -> user.value().toString())).toList();
        lockedUsers.forEach(rooms::lockUser);
        AuthorizedRoom current = rooms.findAuthorizedForUpdate(roomId, actor).orElseThrow(RoomService::unavailable);
        if (current.role() != MembershipRole.OWNER) {
            throw forbidden();
        }
        if (current.room().status() == RoomStatus.ACTIVE) {
            return current; // A retry must not rebuild reminders or change revision again.
        }
        Instant now = clock.instant();
        if (current.room().status() != RoomStatus.ARCHIVED) {
            throw unavailable();
        }
        if (!now.isBefore(current.room().deleteAfter())) {
            throw problem(RoomOperationException.Kind.CONFLICT, "ROOM_RECOVERY_EXPIRED",
                    "Recovery expired", "The room recovery period has ended.");
        }
        checkRevision(current, expectedRevision);
        var members = rooms.findMembers(roomId);
        if (members.stream().anyMatch(member -> !lockedUsers.contains(member.userId()))) {
            throw problem(RoomOperationException.Kind.CONFLICT, "ROOM_MEMBERS_CHANGED",
                    "Room changed", "Reload the room before restoring it.");
        }
        if (rooms.countActiveOwned(actor) >= MAX_OWNED_ROOMS) {
            throw problem(
                    RoomOperationException.Kind.CONFLICT,
                    "OWNED_ROOM_LIMIT_REACHED",
                    "Room limit reached",
                    "This account already owns ten active rooms.");
        }
        for (var member : members) {
            if (member.role() != MembershipRole.OWNER && rooms.countActiveJoined(member.userId()) >= MAX_JOINED_ROOMS) {
                throw problem(
                        RoomOperationException.Kind.CONFLICT,
                        "JOINED_ROOM_LIMIT_REACHED",
                        "Room limit reached",
                        "A participant has reached the active joined-room limit.");
            }
        }
        rooms.restore(roomId, now);
        reminders.ifAvailable(service -> service.replaceForEvent(roomId, current.room().eventAt()));
        return rooms.findAuthorized(roomId, actor).orElseThrow(RoomService::unavailable);
    }

    @Transactional
    public void deletePermanently(UserId actor, RoomId roomId, Long expectedRevision) {
        var room = rooms.findForUpdate(roomId);
        if (room.isEmpty()) {
            return;
        }
        var member = rooms.findMembershipForUpdate(roomId, actor);
        if (member.isEmpty()) {
            return; // Same safe response as an already deleted room; no ID-existence oracle.
        }
        if (member.get().role() != MembershipRole.OWNER) {
            throw forbidden();
        }
        if (room.get().status() != RoomStatus.ARCHIVED || clock.instant().isBefore(room.get().deleteAfter())) {
            throw problem(
                    RoomOperationException.Kind.CONFLICT,
                    "ROOM_DELETE_NOT_ELIGIBLE",
                    "Deletion unavailable",
                    "Archive the room and wait until its deletion eligibility time.");
        }
        checkRevision(new AuthorizedRoom(room.get(), member.get().role()), expectedRevision);
        rooms.deletePermanently(roomId);
    }

    @Transactional
    public int purgeEligible() {
        var due = rooms.lockDeletionDue(clock.instant(), 100);
        due.forEach(rooms::deletePermanently);
        return due.size();
    }

    @Transactional
    public RoomMembership changeRole(
            UserId actor, RoomId roomId, UserId memberId, MembershipRole requestedRole) {
        return changeRole(actor, roomId, memberId, requestedRole, null);
    }

    @Transactional
    public RoomMembership changeRole(UserId actor, RoomId roomId, UserId memberId,
            MembershipRole requestedRole, Long expectedRevision) {
        AuthorizedRoom current = lockedActive(actor, roomId);
        if (current.role() != MembershipRole.OWNER || requestedRole == MembershipRole.OWNER) {
            throw forbidden();
        }
        RoomMembership target = rooms.findMembershipForUpdate(roomId, memberId).orElseThrow(RoomService::unavailable);
        if (target.role() == MembershipRole.OWNER) {
            throw forbidden();
        }
        checkRevision(current, expectedRevision);
        if (target.role() == requestedRole) {
            return target;
        }
        Instant now = clock.instant();
        rooms.incrementRevision(roomId, now);
        rooms.updateRole(roomId, memberId, requestedRole, now);
        return new RoomMembership(roomId, memberId, requestedRole, target.joinedAt(), now);
    }

    @Transactional
    public RoomMembership addMember(RoomId roomId, UserId userId) {
        rooms.lockUser(userId);
        policies.requireCurrent(userId);
        Room room = rooms.findForUpdate(roomId).orElseThrow(RoomService::unavailable);
        if (room.status() != RoomStatus.ACTIVE) {
            throw problem(RoomOperationException.Kind.CONFLICT, "ROOM_ARCHIVED", "Room archived",
                    "Archived rooms cannot be joined.");
        }
        if (room.memberCount() >= MAX_MEMBERS) {
            throw problem(RoomOperationException.Kind.CONFLICT, "ROOM_FULL", "Room full",
                    "This countdown already has 25 members.");
        }
        if (rooms.countActiveJoined(userId) >= MAX_JOINED_ROOMS) {
            throw problem(RoomOperationException.Kind.CONFLICT, "JOINED_ROOM_LIMIT_REACHED",
                    "Joined room limit reached",
                    "This account already belongs to 25 active rooms.");
        }
        Instant now = clock.instant();
        RoomMembership membership = new RoomMembership(roomId, userId, MembershipRole.MEMBER, now, now);
        rooms.addMembership(membership);
        rooms.incrementMemberCount(roomId, now);
        reminderPreferences.ifAvailable(service -> service.createDefaults(roomId, userId));
        return membership;
    }

    @Transactional
    public OwnershipTransfer transfer(UserId actor, RoomId roomId, UserId newOwner) {
        return transfer(actor, roomId, newOwner, null);
    }

    @Transactional
    public OwnershipTransfer transfer(UserId actor, RoomId roomId, UserId newOwner, Long expectedRevision) {
        if (actor.equals(newOwner)) {
            throw validation("INVALID_OWNER", "The target is already the room owner.");
        }
        List<UserId> users = List.of(actor, newOwner).stream()
                .sorted(Comparator.comparing(user -> user.value().toString())).toList();
        users.forEach(rooms::lockUser);
        AuthorizedRoom current = lockedActive(actor, roomId);
        if (current.role() != MembershipRole.OWNER) {
            throw forbidden();
        }
        checkRevision(current, expectedRevision);
        RoomMembership previous = rooms.findMembershipForUpdate(roomId, actor)
                .orElseThrow(RoomService::unavailable);
        RoomMembership target = rooms.findMembershipForUpdate(roomId, newOwner)
                .orElseThrow(RoomService::unavailable);
        if (target.role() == MembershipRole.OWNER) {
            throw validation("INVALID_OWNER", "The target is already the room owner.");
        }
        if (rooms.countActiveOwned(newOwner) >= MAX_OWNED_ROOMS) {
            throw problem(
                    RoomOperationException.Kind.CONFLICT,
                    "OWNED_ROOM_LIMIT_REACHED",
                    "Owned room limit reached",
                    "The selected member cannot own another active room.");
        }
        if (rooms.countActiveJoined(actor) >= MAX_JOINED_ROOMS) {
            throw problem(RoomOperationException.Kind.CONFLICT, "JOINED_ROOM_LIMIT_REACHED",
                    "Joined room limit reached",
                    "The current owner cannot become a co-host in another active room.");
        }
        Instant now = clock.instant();
        rooms.transferOwnership(roomId, actor, newOwner, now);
        rooms.incrementRevision(roomId, now);
        Room room = current.room();
        Room updated = room(room.id(), newOwner, room.title(), room.eventAt(), room.eventTimeZone(),
                room.location(), room.description(), room.status(), room.revision() + 1, room.memberCount(),
                room.archivedAt(), room.deleteAfter(), room.createdAt(), now, room.theme());
        return new OwnershipTransfer(new AuthorizedRoom(updated, MembershipRole.CO_HOST),
                new RoomMembership(roomId, actor, MembershipRole.CO_HOST, previous.joinedAt(), now),
                new RoomMembership(roomId, newOwner, MembershipRole.OWNER, target.joinedAt(), now));
    }

    @Transactional(propagation = Propagation.NEVER)
    public MemberList members(UserId actor, RoomId roomId) {
        var snapshot = memberSnapshots.capture(actor, roomId);
        var reader = profiles.getIfAvailable();
        // Resolve external keys without retaining room locks or a database transaction.
        List<MemberView> items =
                snapshot.members().stream()
                        .map(
                                member ->
                                        new MemberView(
                                                member.userId().value(),
                                                reader == null
                                                        ? "Member"
                                                        : reader.read(member.userId())
                                                                .displayName(),
                                                null,
                                                member.role(),
                                                member.joinedAt(),
                                                member.userId().equals(actor)))
                        .sorted(
                                Comparator.comparing(MemberView::role)
                                        .thenComparing(
                                                MemberView::displayName,
                                                String.CASE_INSENSITIVE_ORDER)
                                        .thenComparing(MemberView::userId))
                        .toList();
        return memberSnapshots.finish(actor, roomId, snapshot, items);
    }

    @Transactional
    public void removeMember(UserId actor, RoomId roomId, UserId memberId, Long expectedRevision) {
        AuthorizedRoom current = lockedActive(actor, roomId);
        if (current.role() == MembershipRole.MEMBER || actor.equals(memberId)) {
            throw forbidden();
        }
        var target = rooms.findMembershipForUpdate(roomId, memberId);
        // An absent target is deliberately indistinguishable from an already removed member.
        if (target.isEmpty()) {
            return;
        }
        if (target.get().role() == MembershipRole.OWNER
                || current.role() == MembershipRole.CO_HOST && target.get().role() != MembershipRole.MEMBER) {
            throw forbidden();
        }
        checkRevision(current, expectedRevision);
        rooms.removeMembership(roomId, memberId, clock.instant());
    }

    private static void checkRevision(AuthorizedRoom current, Long expectedRevision) {
        if (expectedRevision != null && current.room().revision() != expectedRevision) {
            throw revisionMismatch();
        }
    }

    public record MemberView(UUID userId, String displayName, Object photo, MembershipRole role,
            Instant joinedAt, boolean isCurrentUser) {
    }

    public record MemberList(List<MemberView> items, MembershipRole callerRole, RoomStatus status, long revision) {
    }

    private AuthorizedRoom lockedActive(UserId actor, RoomId roomId) {
        AuthorizedRoom room = rooms.findAuthorizedForUpdate(roomId, actor).orElseThrow(RoomService::unavailable);
        if (room.room().status() != RoomStatus.ACTIVE) {
            throw problem(RoomOperationException.Kind.CONFLICT, "ROOM_ARCHIVED", "Room archived",
                    "Archived rooms cannot be changed.");
        }
        return room;
    }

    private Instant resolveUpdate(Room room, UpdateRoomCommand command) {
        boolean any = command.eventLocalDate() != null || command.eventLocalTime() != null
                || command.eventTimeZone() != null;
        if (!any) {
            return room.eventAt();
        }
        ZoneId currentZone = ZoneId.of(room.eventTimeZone());
        LocalDateTime current = LocalDateTime.ofInstant(room.eventAt(), currentZone);
        return resolve(command.eventLocalDate() == null ? current.toLocalDate() : command.eventLocalDate(),
                command.eventLocalTime() == null ? current.toLocalTime() : command.eventLocalTime(),
                command.eventTimeZone() == null ? room.eventTimeZone() : command.eventTimeZone());
    }

    private static Instant resolve(LocalDate date, LocalTime time, String zoneName) {
        try {
            ZoneId zone = ZoneId.of(zoneName);
            LocalDateTime local = LocalDateTime.of(date, time);
            List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
            if (offsets.isEmpty()) {
                throw validation(
                        "EVENT_TIME_NONEXISTENT", "The selected local time does not exist.");
            }
            if (offsets.size() > 1) {
                throw validation("EVENT_TIME_AMBIGUOUS", "The selected local time is ambiguous.");
            }
            return local.toInstant(offsets.getFirst());
        } catch (DateTimeException | NullPointerException exception) {
            throw validation("VALIDATION_FAILED", "The event date, time, or time zone is invalid.");
        }
    }

    private static void requireEditor(MembershipRole role) {
        if (role == MembershipRole.MEMBER) {
            throw forbidden();
        }
    }

    private static Room room(
            RoomId id,
            UserId owner,
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
            Instant updatedAt,
            RoomTheme theme) {
        try {
            RoomTheme selectedTheme = theme == null ? RoomTheme.defaultTheme(id, owner, createdAt) : theme;
            return new Room(id, owner, title, eventAt, eventTimeZone, location, description, selectedTheme,
                    status, revision, memberCount, archivedAt, deleteAfter, createdAt, updatedAt);
        } catch (IllegalArgumentException exception) {
            throw validation("VALIDATION_FAILED", "One or more room fields are invalid.");
        }
    }

    private RoomTheme theme(RoomId roomId, UserId actor, ThemeCommand command, Instant createdAt, Instant now) {
        return theme(roomId, actor, command, createdAt, now, null);
    }

    private RoomTheme theme(
            RoomId roomId,
            UserId actor,
            ThemeCommand command,
            Instant createdAt,
            Instant now,
            UUID retained) {
        if (command == null) {
            throw validation("VALIDATION_FAILED", "The room theme is invalid.");
        }
        try {
            if (command.kind() == RoomTheme.Kind.UPLOADED_IMAGE
                    && (retained == null || !retained.equals(command.mediaAssetId()))) {
                media.getObject().requireReady(actor, command.mediaAssetId());
            }
            return new RoomTheme(
                    roomId,
                    command.kind(),
                    command.presetKey(),
                    command.mediaAssetId(),
                    command.overlayKey(),
                    actor,
                    createdAt,
                    now);
        } catch (IllegalArgumentException exception) {
            throw validation("VALIDATION_FAILED", "The room theme is invalid.");
        }
    }

    private static RoomOperationException unavailable() {
        return problem(
                RoomOperationException.Kind.NOT_FOUND,
                "ROOM_UNAVAILABLE",
                "Room unavailable",
                "The room is unavailable.");
    }

    private static RoomOperationException forbidden() {
        return problem(
                RoomOperationException.Kind.FORBIDDEN,
                "ROOM_ACTION_FORBIDDEN",
                "Action unavailable",
                "This action is not available for the current membership.");
    }

    private static RoomOperationException revisionMismatch() {
        return problem(
                RoomOperationException.Kind.PRECONDITION_FAILED,
                "ROOM_REVISION_MISMATCH",
                "Room changed",
                "The room changed. Reload it before trying again.");
    }

    private static RoomOperationException validation(String code, String detail) {
        return problem(RoomOperationException.Kind.VALIDATION, code, "Validation failed", detail);
    }

    private static RoomOperationException problem(
            RoomOperationException.Kind kind, String code, String title, String detail) {
        return new RoomOperationException(kind, code, title, detail);
    }

    public record CreateRoomCommand(
            String title,
            LocalDate eventLocalDate,
            LocalTime eventLocalTime,
            String eventTimeZone,
            String location,
            String description,
            ThemeCommand theme) {
        public CreateRoomCommand(String title, LocalDate eventLocalDate, LocalTime eventLocalTime,
                String eventTimeZone, String location, String description) {
            this(title, eventLocalDate, eventLocalTime, eventTimeZone, location, description, null);
        }
    }

    public record UpdateRoomCommand(
            String title,
            LocalDate eventLocalDate,
            LocalTime eventLocalTime,
            String eventTimeZone,
            String location,
            boolean locationPresent,
            String description,
            boolean descriptionPresent,
            ThemeCommand theme,
            boolean themePresent) {
        public UpdateRoomCommand(String title, LocalDate eventLocalDate, LocalTime eventLocalTime,
                String eventTimeZone, String location, boolean locationPresent, String description,
                boolean descriptionPresent) {
            this(title, eventLocalDate, eventLocalTime, eventTimeZone, location, locationPresent, description,
                    descriptionPresent, null, false);
        }
    }

    public record ThemeCommand(RoomTheme.Kind kind, String presetKey, String overlayKey, UUID mediaAssetId) {
        public ThemeCommand(RoomTheme.Kind kind, String presetKey, String overlayKey) {
            this(kind, presetKey, overlayKey, null);
        }
    }

    public record OwnershipTransfer(
            AuthorizedRoom room,
            RoomMembership previousOwner,
            RoomMembership newOwner) {
    }
}
