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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactions;

    public InvitationResolver(InvitationRepository invitations, InvitationCryptography crypto, RoomRepository rooms,
            InvitationGuard guard, PreviewReferenceService references, UserAccountRepository accounts,
            UserProfileRepository profiles, PersonalDataCipher personalData, PlatformTransactionManager transactions) {
        this.invitations = invitations;
        this.crypto = crypto;
        this.rooms = rooms;
        this.guard = guard;
        this.references = references;
        this.accounts = accounts;
        this.profiles = profiles;
        this.personalData = personalData;
        this.transactions = new TransactionTemplate(transactions);
    }

    @Transactional(propagation = Propagation.NEVER)
    public Preview preview(String credential, boolean roomCode) {
        // Release database locks before Firestore/KMS calls. Join rechecks the reference generation.
        var snapshot = transactions.execute(status -> capture(credential, roomCode));
        byte[] value = personalData.decrypt(snapshot.room().ownerUserId(), snapshot.keyReference(),
                PersonalDataField.USER_PROFILE_DISPLAY_NAME, snapshot.displayNameCiphertext());
        try {
            var room = snapshot.room();
            var reference = snapshot.reference();
            return new Preview(reference.value(), reference.expiresAt(),
                    new Event(room.title(), room.eventAt(), room.eventTimeZone(),
                            new Theme(room.theme().kind().name(), room.theme().presetKey())),
                    new Inviter(new String(value, StandardCharsets.UTF_8)), room.memberCount(), true);
        } finally {
            Arrays.fill(value, (byte) 0);
        }
    }

    private Snapshot capture(String credential, boolean roomCode) {
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
        var account = accounts.findById(room.ownerUserId()).orElseThrow(InvitationException::invalid);
        var profile = profiles.findByUserId(room.ownerUserId()).orElseThrow(InvitationException::invalid);
        return new Snapshot(room, reference, account.piiKeyReference(), profile.displayNameCiphertext());
    }

    private record Snapshot(Room room, PreviewReferenceService.IssuedReference reference,
            String keyReference, byte[] displayNameCiphertext) {
        @Override
        public String toString() {
            return "Snapshot[REDACTED]";
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
