package com.hyped.app.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hyped.app.account.application.AccountException;
import com.hyped.app.account.application.AccountService;
import com.hyped.app.account.application.port.out.AccountStore;
import com.hyped.app.account.application.port.out.FirebaseAccountAdmin;
import com.hyped.app.identity.application.model.AuthenticationUserProfile;
import com.hyped.app.identity.application.model.VerifiedProviderIdentity;
import com.hyped.app.identity.application.port.out.IdentityLookupProtector;
import com.hyped.app.identity.application.port.out.IdentityTokenVerifier;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.application.port.out.PersonalDataKeyManager;
import com.hyped.app.identity.application.port.out.UserIdentityRepository;
import com.hyped.app.identity.application.service.AuthenticationProfileReader;
import com.hyped.app.identity.domain.IdentityProvider;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.identity.domain.UserIdentity;
import com.hyped.app.room.application.RoomService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AccountDeletionIntegrationTest {
    static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired
    AccountStore store;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RoomService rooms;
    UUID user;
    UUID device;
    UUID session;
    UUID installation;
    AccountService service;
    MutableClock clock;
    FirebaseAccountAdmin firebase;
    PersonalDataKeyManager keys;
    IdentityTokenVerifier verifier;
    UserIdentityRepository identities;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM app.room");
        jdbc.update("DELETE FROM app.app_user");
        clock = new MutableClock();
        user = UUID.randomUUID();
        device = UUID.randomUUID();
        session = UUID.randomUUID();
        installation = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.app_user(id,status,pii_key_reference,created_at,updated_at)
                VALUES (?,'active','key',?,?)
                """, user, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW.minusSeconds(60)));
        jdbc.update("""
                INSERT INTO app.device_registration(id,user_id,platform,installation_id,device_name,
                    last_seen_at,created_at,updated_at) VALUES (?,?,'android',?,'Phone',?,?,?)
                """, device, user, installation, Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO app.auth_session(id,user_id,device_id,token_family_id,issued_at,last_used_at,
                    expires_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)
                """, session, user, device, UUID.randomUUID(), Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(86400)), Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO app.refresh_token_record(id,session_id,token_digest,state,issued_at,expires_at,created_at)
                VALUES (?,?,?,'active',?,?,?)
                """, UUID.randomUUID(), session, new byte[32], Timestamp.from(NOW),
                Timestamp.from(NOW.plusSeconds(86400)), Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO app.user_profile(user_id,display_name_ciphertext,created_at,updated_at) VALUES (?,?,?,?)
                """, user, new byte[] {1}, Timestamp.from(NOW), Timestamp.from(NOW));
        verifier = mock(IdentityTokenVerifier.class);
        identities = mock(UserIdentityRepository.class);
        var lookup = mock(IdentityLookupProtector.class);
        when(lookup.protectProviderSubject(any(), anyString())).thenReturn(new byte[32]);
        var identity = mock(UserIdentity.class);
        when(identity.userId()).thenReturn(new UserId(user));
        when(identities.findByProviderSubject(any(), any())).thenReturn(Optional.of(identity));
        when(verifier.verify("proof")).thenReturn(proof(NOW));
        var cipher = mock(PersonalDataCipher.class);
        when(cipher.encrypt(any(), anyString(), any(), any())).thenAnswer(call ->
                ((byte[]) call.getArgument(3)).clone());
        when(cipher.decrypt(any(), anyString(), any(), any())).thenAnswer(call ->
                ((byte[]) call.getArgument(3)).clone());
        firebase = mock(FirebaseAccountAdmin.class);
        when(firebase.verifiedUid("proof")).thenReturn("private-firebase-uid");
        keys = mock(PersonalDataKeyManager.class);
        var profiles = mock(AuthenticationProfileReader.class);
        when(profiles.read(new UserId(user))).thenReturn(new AuthenticationUserProfile(new UserId(user), "Mira",
                UUID.randomUUID(), "https://private-provider-photo", 1));
        var beans = new StaticListableBeanFactory();
        beans.addBean("firebase", firebase);
        service = new AccountService(store, verifier, lookup, identities, cipher, keys,
                beans.getBeanProvider(FirebaseAccountAdmin.class), profiles, clock, true);
    }
    @Test
    void safeProfileOmitsPrivatePhotoAndSecurityFieldsAndRequiresAnActiveSession() {
        service.requireSession(user, session, installation);
        var profile = service.profile(user);
        assertThat(profile.displayName()).isEqualTo("Mira");
        assertThat(profile.photo()).isNull();
        assertThat(profile.limits().activeDevices()).isEqualTo(1);
        jdbc.update("UPDATE app.auth_session SET revoked_at=?,revoke_reason='device_removed' WHERE id=?",
                Timestamp.from(NOW), session);
        assertThatThrownBy(() -> service.requireSession(user, session, installation))
                .isInstanceOf(AccountException.class).extracting("code").isEqualTo("SESSION_EXPIRED");
    }
    @Test
    void acceptedDeletionPurgesPersonalDataAndDestroysExternalKey() {
        assertThat(service.readiness(user).ready()).isTrue();
        var accepted = service.request(user, "proof");
        assertThat(accepted.irreversible()).isTrue();
        assertThat(accepted.purgeBy()).isEqualTo(NOW.plusSeconds(86400));
        assertThat(status()).isEqualTo("deleted");
        verify(firebase).delete("private-firebase-uid");
        verify(keys).destroyKey(new UserId(user), "key");
        for (String table : new String[] {"auth_session", "device_registration", "user_profile", "user_identity"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app." + table + " WHERE user_id=?",
                    Integer.class, user)).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT pii_key_reference FROM app.app_user WHERE id=?", String.class, user))
                .isNull();
    }
    @Test
    void firebaseFailureImmediatelyRevokesAccessAndDurablyRetries() {
        doThrow(new IllegalStateException("private provider failure")).when(firebase).delete(anyString());
        service.request(user, "proof");
        assertThat(status()).isEqualTo("deletion_pending");
        assertThat(store.activeSession(user, session, installation, NOW)).isFalse();
        assertThat(jdbc.queryForObject("SELECT state FROM app.refresh_token_record WHERE session_id=?",
                String.class, session)).isEqualTo("revoked");
        assertThat(jdbc.queryForObject("SELECT invalidated_at IS NOT NULL FROM app.device_registration WHERE id=?",
                Boolean.class, device)).isTrue();
        verify(keys, never()).destroyKey(any(), anyString());
        clock.now = NOW.plusSeconds(61);
        doNothing().when(firebase).delete(anyString());
        service.tick();
        assertThat(status()).isEqualTo("deleted");
        service.tick();
    }
    @Test
    void keyFailureResumesAfterFirebaseCheckpointWithoutNeedingTheOldToken() {
        doThrow(new IllegalStateException("key unavailable")).when(keys).destroyKey(any(), anyString());
        service.request(user, "proof");
        assertThat(status()).isEqualTo("deletion_pending");
        assertThat(jdbc.queryForObject("SELECT firebase_deleted FROM ops.account_deletion_job WHERE user_id=?",
                Boolean.class, user)).isTrue();
        clock.now = NOW.plusSeconds(61);
        doNothing().when(keys).destroyKey(any(), anyString());
        service.tick();
        verify(firebase).delete("private-firebase-uid");
        assertThat(status()).isEqualTo("deleted");
    }
    @Test
    void staleOrDifferentIdentityCannotDeleteTheAccount() {
        when(verifier.verify("proof")).thenReturn(proof(NOW.minusSeconds(301)));
        assertThatThrownBy(() -> service.request(user, "proof")).isInstanceOf(AccountException.class)
                .extracting("code").isEqualTo("RECENT_AUTHENTICATION_REQUIRED");
        when(verifier.verify("proof")).thenReturn(proof(NOW));
        when(identities.findByProviderSubject(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.request(user, "proof")).isInstanceOf(AccountException.class)
                .extracting("code").isEqualTo("RECENT_AUTHENTICATION_REQUIRED");
        assertThat(status()).isEqualTo("active");
        verify(firebase, never()).delete(anyString());
    }
    @Test
    void ownedArchivedRoomsBlockDeletionWithoutRevokingSession() {
        var room = rooms.create(new UserId(user), new RoomService.CreateRoomCommand("Owned room",
                LocalDate.of(2040, 1, 1), LocalTime.NOON, "UTC", null, null)).room().id();
        rooms.archive(new UserId(user), room);
        assertThat(service.readiness(user).ownedRooms()).hasSize(1);
        assertThatThrownBy(() -> service.request(user, "proof")).hasMessageContaining("Transfer ownership");
        assertThat(status()).isEqualTo("active");
        assertThat(store.activeSession(user, session, installation, NOW)).isTrue();
    }
    @Test
    void deletionClaimsAreLeasedAndStaleCompletionCannotPurgeData() {
        store.begin(user, "key", new byte[] {1}, NOW);
        var first = store.claim(user, NOW);
        assertThat(store.claim(user, NOW)).isNull();
        var next = store.claim(user, NOW.plusSeconds(301));
        assertThat(next.claim()).isNotEqualTo(first.claim());
        store.finish(first, NOW.plusSeconds(302));
        assertThat(status()).isEqualTo("deletion_pending");
    }
    @Test
    void joinedRoomsBlockDeletionAndFreshnessBoundaryIsExactlyFiveMinutes() {
        UUID other = UUID.randomUUID();
        jdbc.update("INSERT INTO app.app_user(id,status,created_at,updated_at) VALUES (?,'active',?,?)",
                other, Timestamp.from(NOW), Timestamp.from(NOW));
        var room = rooms.create(new UserId(other), new RoomService.CreateRoomCommand("Joined room",
                LocalDate.of(2040, 1, 1), LocalTime.NOON, "UTC", null, null)).room().id();
        rooms.addMember(room, new UserId(user));
        assertThat(service.readiness(user).joinedRoomCount()).isEqualTo(1);
        assertThatThrownBy(() -> service.request(user, "proof")).hasMessageContaining("Leave all joined rooms");
        rooms.leave(new UserId(user), room, null);
        when(verifier.verify("proof")).thenReturn(proof(NOW.minusSeconds(300)));
        service.request(user, "proof");
        assertThat(status()).isEqualTo("deleted");
    }

    @Test
    void disabledDeletionFailsBeforeAcceptingOrRevokingAnything() {
        var disabled = new AccountService(store, verifier, mock(IdentityLookupProtector.class), identities,
                mock(PersonalDataCipher.class), keys, new StaticListableBeanFactory()
                .getBeanProvider(FirebaseAccountAdmin.class), mock(AuthenticationProfileReader.class), clock, false);
        assertThatThrownBy(() -> disabled.request(user, "proof")).isInstanceOf(AccountException.class)
                .extracting("code").isEqualTo("ACCOUNT_DELETION_UNAVAILABLE");
        assertThatThrownBy(() -> disabled.readiness(user)).isInstanceOf(AccountException.class);
        assertThat(status()).isEqualTo("active");
        verify(verifier, never()).verify(anyString());
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM app.app_user WHERE id=?", String.class, user);
    }
    private VerifiedProviderIdentity proof(Instant authenticated) {
        return new VerifiedProviderIdentity(IdentityProvider.GOOGLE, "subject", null, false, "Mira", null,
                NOW, authenticated);
    }
    static class MutableClock extends Clock {
        Instant now = NOW;
        @Override
        public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override
        public Clock withZone(ZoneId zone) { return this; }
        @Override
        public Instant instant() { return now; }
    }
}
