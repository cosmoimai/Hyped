package com.hyped.app.room.application;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.room.application.port.out.RoomRepository;
import com.hyped.app.room.application.port.out.RoomRepository.AuthorizedRoom;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.Room;
import com.hyped.app.room.domain.RoomId;
import com.hyped.app.room.domain.RoomMembership;
import com.hyped.app.room.domain.RoomStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {
    public static final int MAX_OWNED_ROOMS = 10;
    public static final int MAX_JOINED_ROOMS = 25;
    public static final int MAX_MEMBERS = 25;
    private final RoomRepository rooms;
    private final Clock clock;

    public RoomService(RoomRepository rooms, Clock clock) {
        this.rooms = rooms;
        this.clock = clock;
    }

    @Transactional
    public AuthorizedRoom create(UserId actor, CreateRoomCommand command) {
        rooms.lockUser(actor);
        if (rooms.countActiveOwned(actor) >= MAX_OWNED_ROOMS) {
            throw problem(RoomOperationException.Kind.CONFLICT, "OWNED_ROOM_LIMIT_REACHED", "Owned room limit reached",
                    "This account already owns ten active rooms.");
        }
        Instant now = clock.instant();
        Instant eventAt = resolve(command.eventLocalDate(), command.eventLocalTime(), command.eventTimeZone());
        if (!eventAt.isAfter(now)) {
            throw validation("EVENT_TIME_NOT_FUTURE", "The event time must be in the future.");
        }
        Room room = room(new RoomId(UUID.randomUUID()), actor, command.title(), eventAt,
                command.eventTimeZone(), command.location(), command.description(), RoomStatus.ACTIVE,
                1, 1, null, null, now, now);
        RoomMembership owner = new RoomMembership(room.id(), actor, MembershipRole.OWNER, now, now);
        rooms.create(room, owner);
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
        Room changed = room(existing.id(), existing.ownerUserId(),
                command.title() == null ? existing.title() : command.title(), eventAt,
                command.eventTimeZone() == null ? existing.eventTimeZone() : command.eventTimeZone(),
                command.locationPresent() ? command.location() : existing.location(),
                command.descriptionPresent() ? command.description() : existing.description(),
                existing.status(), existing.revision() + 1, existing.memberCount(), null, null,
                existing.createdAt(), now);
        if (!rooms.updateVisibleFields(changed, expectedRevision)) {
            throw revisionMismatch();
        }
        return new AuthorizedRoom(changed, current.role());
    }

    @Transactional
    public AuthorizedRoom archive(UserId actor, RoomId roomId) {
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
        Instant now = clock.instant();
        Instant deleteAfter = now.plus(Duration.ofDays(1));
        rooms.archive(roomId, now, deleteAfter, now);
        Room existing = current.room();
        Room archived = room(existing.id(), existing.ownerUserId(), existing.title(), existing.eventAt(),
                existing.eventTimeZone(), existing.location(), existing.description(), RoomStatus.ARCHIVED,
                existing.revision(), existing.memberCount(), now, deleteAfter, existing.createdAt(), now);
        return new AuthorizedRoom(archived, current.role());
    }

    @Transactional
    public RoomMembership changeRole(
            UserId actor, RoomId roomId, UserId memberId, MembershipRole requestedRole) {
        AuthorizedRoom current = lockedActive(actor, roomId);
        if (current.role() != MembershipRole.OWNER || requestedRole == MembershipRole.OWNER) {
            throw forbidden();
        }
        RoomMembership target = rooms.findMembershipForUpdate(roomId, memberId).orElseThrow(RoomService::unavailable);
        if (target.role() == MembershipRole.OWNER) {
            throw forbidden();
        }
        Instant now = clock.instant();
        rooms.updateRole(roomId, memberId, requestedRole, now);
        return new RoomMembership(roomId, memberId, requestedRole, target.joinedAt(), now);
    }

    @Transactional
    public RoomMembership addMember(RoomId roomId, UserId userId) {
        rooms.lockUser(userId);
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
        return membership;
    }

    @Transactional
    public OwnershipTransfer transfer(UserId actor, RoomId roomId, UserId newOwner) {
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
        RoomMembership previous = rooms.findMembershipForUpdate(roomId, actor)
                .orElseThrow(RoomService::unavailable);
        RoomMembership target = rooms.findMembershipForUpdate(roomId, newOwner)
                .orElseThrow(RoomService::unavailable);
        if (target.role() == MembershipRole.OWNER) {
            throw validation("INVALID_OWNER", "The target is already the room owner.");
        }
        if (rooms.countActiveOwned(newOwner) >= MAX_OWNED_ROOMS) {
            throw problem(RoomOperationException.Kind.CONFLICT, "OWNED_ROOM_LIMIT_REACHED", "Owned room limit reached",
                    "The selected member cannot own another active room.");
        }
        if (rooms.countActiveJoined(actor) >= MAX_JOINED_ROOMS) {
            throw problem(RoomOperationException.Kind.CONFLICT, "JOINED_ROOM_LIMIT_REACHED",
                    "Joined room limit reached",
                    "The current owner cannot become a co-host in another active room.");
        }
        Instant now = clock.instant();
        rooms.transferOwnership(roomId, actor, newOwner, now);
        Room room = current.room();
        Room updated = room(room.id(), newOwner, room.title(), room.eventAt(), room.eventTimeZone(),
                room.location(), room.description(), room.status(), room.revision(), room.memberCount(),
                room.archivedAt(), room.deleteAfter(), room.createdAt(), now);
        return new OwnershipTransfer(new AuthorizedRoom(updated, MembershipRole.CO_HOST),
                new RoomMembership(roomId, actor, MembershipRole.CO_HOST, previous.joinedAt(), now),
                new RoomMembership(roomId, newOwner, MembershipRole.OWNER, target.joinedAt(), now));
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
                throw validation("EVENT_TIME_NONEXISTENT", "The selected local time does not exist.");
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
            Instant updatedAt) {
        try {
            return new Room(id, owner, title, eventAt, eventTimeZone, location, description, status,
                    revision, memberCount, archivedAt, deleteAfter, createdAt, updatedAt);
        } catch (IllegalArgumentException exception) {
            throw validation("VALIDATION_FAILED", "One or more room fields are invalid.");
        }
    }

    private static RoomOperationException unavailable() {
        return problem(RoomOperationException.Kind.NOT_FOUND, "ROOM_UNAVAILABLE", "Room unavailable",
                "The room is unavailable.");
    }

    private static RoomOperationException forbidden() {
        return problem(RoomOperationException.Kind.FORBIDDEN, "ROOM_ACTION_FORBIDDEN", "Action unavailable",
                "This action is not available for the current membership.");
    }

    private static RoomOperationException revisionMismatch() {
        return problem(RoomOperationException.Kind.PRECONDITION_FAILED, "ROOM_REVISION_MISMATCH", "Room changed",
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
            String description) {
    }

    public record UpdateRoomCommand(
            String title,
            LocalDate eventLocalDate,
            LocalTime eventLocalTime,
            String eventTimeZone,
            String location,
            boolean locationPresent,
            String description,
            boolean descriptionPresent) {
    }

    public record OwnershipTransfer(
            AuthorizedRoom room,
            RoomMembership previousOwner,
            RoomMembership newOwner) {
    }
}
