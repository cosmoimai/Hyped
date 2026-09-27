package com.hyped.app.invitation.application;

import com.hyped.app.invitation.application.port.out.InvitationCryptography;
import com.hyped.app.room.domain.RoomId;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Opaque, ten-minute reference; generation and channel are authenticated, never client supplied. */
@Service
@ConditionalOnProperty(prefix = "hyped.invitation", name = "enabled", havingValue = "true")
public class PreviewReferenceService {
    private final InvitationCryptography crypto;
    private final Clock clock;

    public PreviewReferenceService(InvitationCryptography crypto, Clock clock) {
        this.crypto = crypto;
        this.clock = clock;
    }

    public IssuedReference issue(Invitation invitation, boolean roomCode) {
        Instant expires = clock.instant().plusSeconds(600);
        String value = invitation.roomId().value() + ":" + invitation.generation() + ":"
                + (roomCode ? "room_code" : "invite_link") + ":" + expires.getEpochSecond();
        return new IssuedReference(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(crypto.encrypt("preview", value)), expires);
    }

    public Reference resolve(String value, boolean roomCode) {
        if (value == null || value.length() > 1024) {
            throw InvitationException.invalid();
        }
        Reference reference;
        try {
            String[] parts = crypto.decrypt("preview", Base64.getUrlDecoder().decode(value)).split(":");
            if (parts.length != 4 || !parts[2].equals(roomCode ? "room_code" : "invite_link")) {
                throw new IllegalArgumentException();
            }
            reference = new Reference(new RoomId(UUID.fromString(parts[0])), Integer.parseInt(parts[1]),
                    Instant.ofEpochSecond(Long.parseLong(parts[3])));
        } catch (IllegalArgumentException exception) {
            throw InvitationException.invalid();
        }
        if (!reference.expiresAt().isAfter(clock.instant())) {
            throw new InvitationException(410, "PREVIEW_EXPIRED");
        }
        return reference;
    }

    public record Reference(RoomId roomId, int generation, Instant expiresAt) {
        @Override
        public String toString() {
            return "Reference[REDACTED]";
        }
    }

    public record IssuedReference(String value, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedReference[REDACTED]";
        }
    }
}
