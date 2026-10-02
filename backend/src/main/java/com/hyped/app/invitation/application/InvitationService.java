package com.hyped.app.invitation.application;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.invitation.application.port.out.InvitationCryptography;
import com.hyped.app.invitation.application.port.out.InvitationRepository;
import com.hyped.app.room.application.port.out.RoomRepository;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.RoomId;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "hyped.invitation", name = "enabled", havingValue = "true")
public class InvitationService {
    private static final int MAX_COLLISION_ATTEMPTS = 5;
    private final InvitationRepository invitations;
    private final InvitationCryptography crypto;
    private final RoomRepository rooms;
    private final InvitationGuard guard;
    private final InvitationReplayService replays;
    private final Clock clock;

    public InvitationService(InvitationRepository invitations, InvitationCryptography crypto, RoomRepository rooms,
            InvitationGuard guard, InvitationReplayService replays, Clock clock) {
        this.invitations = invitations;
        this.crypto = crypto;
        this.rooms = rooms;
        this.guard = guard;
        this.replays = replays;
        this.clock = clock;
    }

    /** Also provisions credentials for rooms predating V6, without changing the documented GET contract. */
    @Transactional
    public Share get(UserId actor, RoomId roomId) {
        guard.lockActor(actor);
        authorize(actor, roomId, false);
        return share(invitations.find(roomId).orElseGet(() -> generate(actor, roomId, 1)));
    }

    @Transactional(noRollbackFor = RecordedInvitationException.class)
    public Share rotate(UserId actor, RoomId roomId, String key) {
        replays.claim(actor, "rotate", key);
        guard.lockActor(actor);
        String request = roomId.value().toString();
        var previous = replays.find(actor, "rotate", key, request, Share.class);
        try {
            authorize(actor, roomId, true);
        } catch (InvitationException failure) {
            if (previous.isEmpty()) {
                replays.reject(actor, "rotate", key, request, roomId, failure);
            }
            throw failure;
        }
        if (previous.isPresent()) {
            return previous.get();
        }
        int generation = invitations.find(roomId).map(invitation -> invitation.generation() + 1).orElse(1);
        Share result = share(generate(actor, roomId, generation));
        replays.remember(actor, "rotate", key, request, result, roomId, 200);
        return result;
    }

    private void authorize(UserId actor, RoomId roomId, boolean rotation) {
        var room = rooms.findForUpdate(roomId)
                .orElseThrow(() -> new InvitationException(404, "ROOM_UNAVAILABLE"));
        var member = rooms.findMembershipForUpdate(roomId, actor)
                .orElseThrow(() -> new InvitationException(404, "ROOM_UNAVAILABLE"));
        if (rotation && member.role() != MembershipRole.OWNER) {
            throw new InvitationException(403, "ROOM_ACTION_FORBIDDEN");
        }
        guard.requireActive(room);
    }

    private Invitation generate(UserId actor, RoomId roomId, int generation) {
        var previous = invitations.find(roomId);
        for (int attempt = 0; attempt < MAX_COLLISION_ATTEMPTS; attempt++) {
            String token = crypto.newToken();
            String code = crypto.newCode();
            byte[] tokenHash = crypto.digest("link", token);
            byte[] codeHmac = crypto.digest("code", code);
            if (previous.isPresent() && (MessageDigest.isEqual(previous.get().linkTokenHash(), tokenHash)
                    || MessageDigest.isEqual(previous.get().roomCodeHmac(), codeHmac))) {
                continue;
            }
            Instant now = clock.instant();
            Invitation invitation = new Invitation(roomId, generation, tokenHash,
                    codeHmac, crypto.encrypt(context(roomId, generation, "link"), token),
                    crypto.encrypt(context(roomId, generation, "code"), code), actor, now, now);
            if (invitations.replace(invitation)) {
                return invitation;
            }
        }
        throw new InvitationException(503, "INVITATION_UNAVAILABLE");
    }

    private Share share(Invitation invitation) {
        String token = crypto.decrypt(context(invitation.roomId(), invitation.generation(), "link"),
                invitation.linkTokenCiphertext());
        String code = crypto.decrypt(context(invitation.roomId(), invitation.generation(), "code"),
                invitation.roomCodeCiphertext());
        return new Share(crypto.inviteUrl(token), code.substring(0, 4) + "-" + code.substring(4),
                invitation.generation(), "ROOM_END");
    }

    private static String context(RoomId room, int generation, String kind) {
        return room.value() + ":" + generation + ":" + kind;
    }

    public record Share(String inviteUrl, String roomCode, int generation, String validUntil) {
        @Override
        public String toString() {
            return "Share[REDACTED]";
        }
    }
}
