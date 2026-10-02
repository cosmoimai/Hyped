package com.hyped.app.invitation.application;

import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.invitation.application.port.out.InvitationCryptography;
import com.hyped.app.invitation.application.port.out.InvitationRepository;
import com.hyped.app.profile.application.port.out.UserProfileRepository;
import com.hyped.app.room.application.port.out.RoomRepository;
import com.hyped.app.room.domain.Room;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "hyped.invitation", name = "enabled", havingValue = "true")
public class InvitationResolver {
    private final InvitationRepository invitations;
    private final InvitationCryptography crypto;
    private final RoomRepository rooms;
    private final InvitationGuard guard;
    private final PreviewReferenceService references;
    private final UserAccountRepository accounts;
    private final UserProfileRepository profiles;
    private final PersonalDataCipher personalData;

    public InvitationResolver(InvitationRepository invitations, InvitationCryptography crypto, RoomRepository rooms,
            InvitationGuard guard, PreviewReferenceService references, UserAccountRepository accounts,
            UserProfileRepository profiles, PersonalDataCipher personalData) {
        this.invitations = invitations;
        this.crypto = crypto;
        this.rooms = rooms;
        this.guard = guard;
        this.references = references;
        this.accounts = accounts;
        this.profiles = profiles;
        this.personalData = personalData;
    }

    @Transactional
    public Preview preview(String credential, boolean roomCode) {
        String canonical = canonical(credential, roomCode);
        byte[] digest = crypto.digest(roomCode ? "code" : "link", canonical);
        Invitation candidate = invitations.findCredential(digest, roomCode).orElseThrow(InvitationException::invalid);
        // Preview takes only the room lock; it never acquires any account lock. Rotation uses the same room lock.
        Room room = rooms.findForUpdate(candidate.roomId()).orElseThrow(InvitationException::invalid);
        Invitation current = invitations.find(candidate.roomId()).orElseThrow(InvitationException::invalid);
        if (!MessageDigest.isEqual(digest, roomCode ? current.roomCodeHmac() : current.linkTokenHash())) {
            throw InvitationException.invalid();
        }
        guard.requireActive(room);
        var reference = references.issue(current, roomCode);
        return new Preview(reference.value(), reference.expiresAt(),
                new Event(room.title(), room.eventAt(), room.eventTimeZone(), new Theme("PRESET", "soft-blue-01")),
                new Inviter(displayName(room)), room.memberCount(), true);
    }

    private String displayName(Room room) {
        var account = accounts.findById(room.ownerUserId()).orElseThrow(InvitationException::invalid);
        var profile = profiles.findByUserId(room.ownerUserId()).orElseThrow(InvitationException::invalid);
        byte[] value = personalData.decrypt(room.ownerUserId(), account.piiKeyReference(),
                PersonalDataField.USER_PROFILE_DISPLAY_NAME, profile.displayNameCiphertext());
        try {
            return new String(value, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(value, (byte) 0);
        }
    }

    private static String canonical(String credential, boolean roomCode) {
        if (credential == null || credential.length() > 128) {
            throw InvitationException.invalid();
        }
        String value = roomCode ? credential.replace("-", "").toUpperCase(Locale.ROOT) : credential;
        if (!value.matches(roomCode ? "[A-HJ-NP-Z2-9]{8}" : "[A-Za-z0-9_-]{43}")) {
            throw InvitationException.invalid();
        }
        return value;
    }

    public record Preview(String previewReference, Instant expiresAt, Event event, Inviter inviter,
            int memberCount, boolean requiresAuthentication) {
        @Override
        public String toString() {
            return "Preview[REDACTED]";
        }
    }

    public record Event(String title, Instant eventAt, String eventTimeZone, Theme theme) {
    }

    public record Theme(String kind, String presetKey) {
    }

    public record Inviter(String displayName) {
    }
}
