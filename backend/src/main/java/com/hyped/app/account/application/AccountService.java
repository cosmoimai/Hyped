package com.hyped.app.account.application;

import com.hyped.app.account.application.port.out.AccountStore;
import com.hyped.app.account.application.port.out.FirebaseAccountAdmin;
import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.application.port.out.IdentityLookupProtector;
import com.hyped.app.identity.application.port.out.IdentityTokenVerifier;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.application.port.out.PersonalDataKeyManager;
import com.hyped.app.identity.application.port.out.UserIdentityRepository;
import com.hyped.app.identity.application.service.AuthenticationProfileReader;
import com.hyped.app.identity.domain.UserId;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "true")
public class AccountService {
    private final AccountStore store;
    private final IdentityTokenVerifier verifier;
    private final IdentityLookupProtector lookup;
    private final UserIdentityRepository identities;
    private final PersonalDataCipher cipher;
    private final PersonalDataKeyManager keys;
    private final org.springframework.beans.factory.ObjectProvider<FirebaseAccountAdmin> firebase;
    private final AuthenticationProfileReader profiles;
    private final Clock clock;
    private final boolean deletionEnabled;

    public AccountService(
            AccountStore store,
            IdentityTokenVerifier verifier,
            IdentityLookupProtector lookup,
            UserIdentityRepository identities,
            PersonalDataCipher cipher,
            PersonalDataKeyManager keys,
            org.springframework.beans.factory.ObjectProvider<FirebaseAccountAdmin> firebase,
            AuthenticationProfileReader profiles,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value(
                            "${hyped.account-deletion.enabled:false}")
                    boolean deletionEnabled) {
        this.store = store;
        this.verifier = verifier;
        this.lookup = lookup;
        this.identities = identities;
        this.cipher = cipher;
        this.keys = keys;
        this.firebase = firebase;
        this.profiles = profiles;
        this.clock = clock;
        this.deletionEnabled = deletionEnabled;
    }
    public void requireSession(UUID actor, UUID session, UUID installation) {
        if (!store.activeSession(actor, session, installation, clock.instant())) {
            throw new AccountException(401, "SESSION_EXPIRED", "Your session is no longer active.");
        }
    }
    public record Limits(int activeDevices, int maximumDevices, int ownedActiveRooms, int maximumOwnedActiveRooms,
            int joinedActiveRooms, int maximumJoinedActiveRooms) { }
    public record Profile(UUID id, String displayName, Void photo, long profileRevision, Limits limits) { }
    public Profile profile(UUID actor) {
        var profile = profiles.read(new UserId(actor));
        return new Profile(actor, profile.displayName(), null, profile.profileRevision(),
                new Limits(store.activeDevices(actor, clock.instant()), 5,
                        store.activeRooms(actor, true), 10, store.activeRooms(actor, false), 25));
    }
    public AccountStore.Readiness readiness(UUID actor) {
        requireDeletionEnabled();
        return store.readiness(actor);
    }
    public record Accepted(String status, boolean irreversible, Instant purgeBy) { }
    public Accepted request(UUID actor, String token) {
        requireDeletionEnabled();
        com.hyped.app.identity.application.model.VerifiedProviderIdentity identity;
        try {
            identity = verifier.verify(token);
        } catch (com.hyped.app.identity.application.exception.IdentityTokenVerificationException failure) {
            if (failure.reason() == com.hyped.app.identity.application.exception.IdentityTokenVerificationException
                    .Reason.VERIFIER_UNAVAILABLE) {
                throw new AccountException(
                        503,
                        "DEPENDENCY_UNAVAILABLE",
                        "Authentication is temporarily unavailable.");
            }
            throw recent();
        }
        Instant now = clock.instant();
        if (identity.authenticatedAt().isBefore(now.minusSeconds(300)) || identity.authenticatedAt().isAfter(now)) {
            throw recent();
        }
        var owner = identities.findByProviderSubject(identity.provider(),
                lookup.protectProviderSubject(identity.provider(), identity.providerSubject()));
        if (owner.isEmpty() || !owner.get().userId().value().equals(actor)) { throw recent(); }
        var admin = firebase.getIfAvailable();
        if (admin == null) {
            throw new AccountException(
                    503, "DEPENDENCY_UNAVAILABLE", "Account deletion unavailable.");
        }
        String uid = admin.verifiedUid(token);
        String reference = store.keyReference(actor);
        byte[] plaintext = uid.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted;
        try {
            encrypted = cipher.encrypt(new UserId(actor), reference, PersonalDataField.ACCOUNT_DELETION_FIREBASE_UID,
                    plaintext);
        } finally { Arrays.fill(plaintext, (byte) 0); }
        store.begin(actor, reference, encrypted, now); // Commit irreversible revocation before external calls.
        try { process(actor); } catch (RuntimeException failure) { /* Committed request remains irreversible. */ }
        return new Accepted("DELETION_PENDING", true, now.plusSeconds(86400));
    }
    private org.springframework.beans.factory.ObjectProvider<com.hyped.app.media.application.MediaService> media;

    @org.springframework.beans.factory.annotation.Autowired
    public void media(
            org.springframework.beans.factory.ObjectProvider<
                            com.hyped.app.media.application.MediaService>
                    provider) {
        this.media = provider;
    }

    public void tick() { store.due(clock.instant()).forEach(this::process); }
    public void process(UUID actor) {
        var job = store.claim(actor, clock.instant());
        if (job == null) { return; }
        byte[] uid = null;
        try {
            if (!job.firebaseDeleted()) {
                uid = cipher.decrypt(new UserId(actor), job.keyReference(),
                        PersonalDataField.ACCOUNT_DELETION_FIREBASE_UID, job.firebaseUid());
                var admin = firebase.getIfAvailable();
                if (admin == null) {
                    throw new IllegalStateException("Account cleanup unavailable");
                }
                admin.delete(new String(uid, StandardCharsets.UTF_8));
                if (!store.firebaseDeleted(job)) { return; } // Durable checkpoint before key destruction.
            }
            if (media != null) { media.ifAvailable(service -> service.purgeUser(actor)); }
            keys.destroyKey(new UserId(actor), job.keyReference());
            store.finish(job, clock.instant());
        } catch (RuntimeException failure) {
            // Durable retry; provider exception text and identifying values must never escape or be logged.
            store.retry(job, clock.instant());
        } finally { if (uid != null) { Arrays.fill(uid, (byte) 0); } }
    }
    private void requireDeletionEnabled() {
        if (!deletionEnabled) {
            throw new AccountException(503, "ACCOUNT_DELETION_UNAVAILABLE",
                    "Account deletion is temporarily unavailable.");
        }
    }
    private static AccountException recent() {
        return new AccountException(
                401, "RECENT_AUTHENTICATION_REQUIRED", "Sign in again to confirm deletion.");
    }
}
