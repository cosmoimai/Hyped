package com.hyped.app.room.application;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.room.application.port.out.RoomRepository;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.RoomId;
import com.hyped.app.room.domain.RoomMembership;
import com.hyped.app.room.domain.RoomStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Short authorized snapshots bracket decryption without retaining locks across external key calls.
 */
@Service
public class RoomMemberSnapshotService {
    private final RoomRepository rooms;
    private final com.hyped.app.moderation.application.port.out.SafetyStore safety;

    public RoomMemberSnapshotService(
            RoomRepository rooms,
            com.hyped.app.moderation.application.port.out.SafetyStore safety) {
        this.safety = safety;
        this.rooms = rooms;
    }

    public record Snapshot(
            List<RoomMembership> members,
            MembershipRole callerRole,
            RoomStatus status,
            long revision,
            java.util.Set<java.util.UUID> hidden) {
        public Snapshot {
            members = List.copyOf(members);
        }
    }

    @Transactional
    public Snapshot capture(UserId actor, RoomId roomId) {
        var current =
                rooms.findAuthorizedForUpdate(roomId, actor)
                        .orElseThrow(RoomMemberSnapshotService::unavailable);
        if (current.room().status() == RoomStatus.DELETING) {
            throw unavailable();
        }
        return new Snapshot(
                rooms.findMembers(roomId),
                current.role(),
                current.room().status(),
                current.room().revision(),
                safety.hidden(actor.value(), roomId.value()));
    }

    @Transactional
    public RoomService.MemberList finish(
            UserId actor, RoomId roomId, Snapshot expected, List<RoomService.MemberView> items) {
        // Re-authorize after decryption; a removed caller must never receive the old view.
        Snapshot current = capture(actor, roomId);
        if (!current.equals(expected)) {
            throw new RoomOperationException(
                    RoomOperationException.Kind.PRECONDITION_FAILED,
                    "ROOM_REVISION_MISMATCH",
                    "Room changed",
                    "This room changed. Reload members before trying again.");
        }
        return new RoomService.MemberList(
                List.copyOf(items), current.callerRole(), current.status(), current.revision());
    }

    private static RoomOperationException unavailable() {
        return new RoomOperationException(
                RoomOperationException.Kind.NOT_FOUND,
                "ROOM_UNAVAILABLE",
                "Room unavailable",
                "The room is unavailable.");
    }
}
