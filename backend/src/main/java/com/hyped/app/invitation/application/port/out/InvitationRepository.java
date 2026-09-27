package com.hyped.app.invitation.application.port.out;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.invitation.application.Invitation;
import com.hyped.app.room.domain.RoomId;
import java.time.Instant;
import java.util.Optional;

public interface InvitationRepository {
    Optional<Invitation> find(RoomId room);

    Optional<Invitation> findCredential(byte[] digest, boolean roomCode);

    /** Requires the room lock. False means a credential collision; the surrounding transaction stays usable. */
    boolean replace(Invitation invitation);

    boolean claim(long lockKey);

    Optional<Replay> replay(UserId actor, String operation, String key, Instant now);

    void remember(UserId actor, String operation, String key, byte[] request, byte[] response,
            RoomId resource, int status, Instant now, Instant expiresAt);

    record Replay(byte[] requestDigest, byte[] responseCiphertext, int status) {
        @Override
        public String toString() {
            return "Replay[REDACTED]";
        }
    }
}
