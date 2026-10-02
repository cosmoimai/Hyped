package com.hyped.app.membership.application;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.invitation.application.InvitationException;
import com.hyped.app.invitation.application.InvitationGuard;
import com.hyped.app.invitation.application.InvitationReplayService;
import com.hyped.app.invitation.application.PreviewReferenceService;
import com.hyped.app.invitation.application.RecordedInvitationException;
import com.hyped.app.invitation.application.port.out.InvitationRepository;
import com.hyped.app.room.application.RoomService;
import com.hyped.app.room.application.port.out.RoomRepository.AuthorizedRoom;
import com.hyped.app.room.application.port.out.RoomRepository;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.Room;
import com.hyped.app.room.domain.RoomMembership;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "hyped.invitation", name = "enabled", havingValue = "true")
public class JoinRoomService {
    private final RoomRepository rooms;
    private final InvitationRepository invitations;
    private final PreviewReferenceService references;
    private final InvitationGuard guard;
    private final InvitationReplayService replays;
    private final Clock clock;

    public JoinRoomService(RoomRepository rooms, InvitationRepository invitations, PreviewReferenceService references,
            InvitationGuard guard, InvitationReplayService replays, Clock clock) {
        this.rooms = rooms;
        this.invitations = invitations;
        this.references = references;
        this.guard = guard;
        this.replays = replays;
        this.clock = clock;
    }

    /**
     * Account -> room -> membership -> invitation. Account serializes the cross-room user limit; room serializes
     * capacity, duplicates, archive and rotation. The room-lock winner commits first. If rotation commits first,
     * the old reference fails; if join commits first, its membership survives rotation. No inverse account locks.
     */
    @Transactional(noRollbackFor = RecordedInvitationException.class)
    public JoinResult joinByPreviewReference(UserId actor, String value, boolean roomCode, String key) {
        String operation = roomCode ? "join-code" : "join-link";
        replays.claim(actor, operation, key);
        guard.lockActor(actor);
        var replay = replays.find(actor, operation, key, value, JoinResult.class);
        CheckedJoin checked;
        try {
            checked = check(actor, value, roomCode);
        } catch (InvitationException failure) {
            if (replay.isEmpty()) {
                replays.reject(actor, operation, key, value, null, failure);
            }
            throw failure;
        }
        Room room = checked.room();
        var member = checked.member();
        if (replay.isPresent()) {
            if (member.isEmpty()) {
                throw new InvitationException(404, "ROOM_UNAVAILABLE");
            }
            return replay.get();
        }
        if (member.isEmpty()) {
            Instant now = clock.instant();
            rooms.addMembership(new RoomMembership(room.id(), actor, MembershipRole.MEMBER, now, now),
                    roomCode ? "room_code" : "invite_link");
            rooms.incrementMemberCount(room.id(), now);
        }
        JoinResult result = new JoinResult(rooms.findAuthorized(room.id(), actor).orElseThrow(),
                member.isEmpty(), clock.instant());
        replays.remember(actor, operation, key, value, result, room.id(), result.created() ? 201 : 200);
        return result;
    }

    private CheckedJoin check(UserId actor, String value, boolean roomCode) {
        var reference = references.resolve(value, roomCode);
        var room = rooms.findForUpdate(reference.roomId())
                .orElseThrow(() -> new InvitationException(410, "ROOM_ENDED"));
        var member = rooms.findMembershipForUpdate(room.id(), actor);
        var invitation = invitations.find(room.id()).orElseThrow(InvitationException::invalid);
        // Recheck time and generation after potentially waiting for both locks.
        references.resolve(value, roomCode);
        if (invitation.generation() != reference.generation()) {
            throw InvitationException.invalid();
        }
        guard.requireActive(room);
        if (member.isEmpty()) {
            if (room.memberCount() >= RoomService.MAX_MEMBERS) {
                throw new InvitationException(409, "ROOM_FULL");
            }
            if (rooms.countActiveJoined(actor) >= RoomService.MAX_JOINED_ROOMS) {
                throw new InvitationException(409, "JOINED_ROOM_LIMIT_REACHED");
            }
        }
        return new CheckedJoin(room, member);
    }

    private record CheckedJoin(Room room, Optional<RoomMembership> member) {
    }

    public record JoinResult(AuthorizedRoom room, boolean created, Instant serverNow) {
        @Override
        public String toString() {
            return "JoinResult[REDACTED]";
        }
    }
}
