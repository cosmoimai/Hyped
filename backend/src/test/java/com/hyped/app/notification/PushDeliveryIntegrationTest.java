package com.hyped.app.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.hyped.app.common.api.ApiProblemException;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.notification.application.PushRegistrationService;
import com.hyped.app.notification.application.ReminderDeliveryService;
import com.hyped.app.notification.application.port.out.PushSender;
import com.hyped.app.notification.application.port.out.ReminderDeliveryRepository;
import com.hyped.app.reminder.domain.RoomReminder;
import com.hyped.app.room.application.RoomService;
import com.hyped.app.room.domain.RoomId;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"hyped.push.enabled=true", "hyped.push.scheduler-audience=https://scheduler.test",
        "hyped.push.scheduler-email=scheduler@test.iam.gserviceaccount.com"})
@Testcontainers(disabledWithoutDocker = true)
@Import(PushDeliveryIntegrationTest.Config.class)
class PushDeliveryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PushRegistrationService registrations;
    @Autowired
    ReminderDeliveryService deliveries;
    @Autowired
    ReminderDeliveryRepository records;
    @Autowired
    RoomService rooms;
    @Autowired
    MutableClock clock;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoBean
    PersonalDataCipher cipher;
    @MockitoBean
    PushSender sender;

    private UUID user;
    private UUID installation;
    private UUID device;
    private UUID session;
    private RoomId room;
    private static final Instant NOW = Instant.parse("2030-12-19T10:00:00Z");

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM app.room");
        jdbc.update("DELETE FROM app.app_user");
        clock.now = NOW.minusSeconds(60);
        user = UUID.randomUUID();
        installation = UUID.randomUUID();
        device = UUID.randomUUID();
        session = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.app_user(id,status,pii_key_reference,created_at,updated_at)
                VALUES (?,'active','test-key',?,?)
                """, user, java.sql.Timestamp.from(clock.now), java.sql.Timestamp.from(clock.now));
        jdbc.update("""
                INSERT INTO app.device_registration(id,user_id,platform,installation_id,device_name,
                    last_seen_at,created_at,updated_at) VALUES (?,?,'android',?,'Test',?,?,?)
                """, device, user, installation, java.sql.Timestamp.from(clock.now),
                java.sql.Timestamp.from(clock.now), java.sql.Timestamp.from(clock.now));
        jdbc.update("""
                INSERT INTO app.auth_session(id,user_id,device_id,token_family_id,issued_at,last_used_at,
                    expires_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)
                """, session, user, device, UUID.randomUUID(), java.sql.Timestamp.from(clock.now),
                java.sql.Timestamp.from(clock.now), java.sql.Timestamp.from(NOW.plusSeconds(172800)),
                java.sql.Timestamp.from(clock.now), java.sql.Timestamp.from(clock.now));
        when(cipher.encrypt(any(), anyString(), any(), any())).thenAnswer(invocation ->
                ("cipher:" + new String(invocation.getArgument(3), StandardCharsets.UTF_8))
                        .getBytes(StandardCharsets.UTF_8));
        when(cipher.decrypt(any(), anyString(), any(), any())).thenAnswer(invocation ->
                new String((byte[]) invocation.getArgument(3), StandardCharsets.UTF_8)
                        .substring(7).getBytes(StandardCharsets.UTF_8));
        when(sender.send(anyString(), any())).thenReturn(PushSender.Outcome.SENT);
        clearInvocations(sender);
        com.hyped.app.policy.AcceptedPolicyFixture.accept(jdbc, user);
        room = rooms.create(new com.hyped.app.identity.domain.UserId(user), new RoomService.CreateRoomCommand(
                "Private title", LocalDate.of(2030, 12, 20), LocalTime.of(10, 0), "UTC", null, null)).room().id();
        registrations.register(user, session, installation, "token-one", true);
        clock.now = NOW;
    }

    @Test
    void removingMemberCancelsClaimedDeliveryAndPreventsFutureReminders() {
        var oldOwner = new com.hyped.app.identity.domain.UserId(user);
        UUID next = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.app_user(id,status,created_at,updated_at) VALUES (?,'active',?,?)
                """, next, java.sql.Timestamp.from(clock.now), java.sql.Timestamp.from(clock.now));
        com.hyped.app.policy.AcceptedPolicyFixture.accept(jdbc, next);
        var newOwner = new com.hyped.app.identity.domain.UserId(next);
        rooms.addMember(room, newOwner);
        rooms.transfer(oldOwner, room, newOwner);
        records.prepare(100, clock.now);
        var claimed = records.claim(100, clock.now).getFirst();
        rooms.removeMember(newOwner, room, oldOwner, null);
        assertThat(records.stillEligible(claimed, clock.now)).isFalse();
        assertThat(count("canceled")).isEqualTo(1);
        clock.now = NOW.plusSeconds(86400);
        assertThat(deliveries.tick().sent()).isZero();
        verify(sender, times(0)).send(anyString(), any());
    }

    @Test
    void archiveCancelsClaimedDeliveriesAndRestoreDoesNotRepeatPastBoundaries() {
        records.prepare(100, clock.now);
        var claimed = records.claim(100, clock.now).getFirst();
        var actor = new com.hyped.app.identity.domain.UserId(user);
        rooms.archive(actor, room);
        assertThat(count("canceled")).isEqualTo(1);
        assertThat(records.stillEligible(claimed, clock.now)).isFalse();
        clock.now = NOW.plusSeconds(1);
        rooms.restore(actor, room, 2L);
        assertThat(deliveries.tick().sent()).isZero();
        clock.now = Instant.parse("2030-12-20T09:00:00Z");
        assertThat(deliveries.tick().sent()).isEqualTo(1);
    }

    @Test
    void permanentDeletionRemovesDeviceDeliveryRecords() {
        records.prepare(100, clock.now);
        var actor = new com.hyped.app.identity.domain.UserId(user);
        rooms.archive(actor, room);
        clock.now = NOW.plusSeconds(86400);
        rooms.deletePermanently(actor, room, 2L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.reminder_delivery WHERE room_id = ?",
                Integer.class, room.value())).isZero();
        assertThat(deliveries.tick().sent()).isZero();
    }

    @Test
    void registrationBindsSessionOwnerAndEncryptsWithoutExposingToken() {
        assertThat(jdbc.queryForObject("SELECT push_session_id FROM app.device_registration WHERE id = ?",
                UUID.class, device)).isEqualTo(session);
        assertThat(new String(jdbc.queryForObject(
                "SELECT fcm_token_ciphertext FROM app.device_registration WHERE id = ?", byte[].class, device),
                StandardCharsets.UTF_8)).isEqualTo("cipher:token-one");
        assertThatThrownBy(() -> registrations.register(UUID.randomUUID(), session, installation, "foreign", true))
                .isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(() -> registrations.register(user, session, UUID.randomUUID(), "foreign", true))
                .isInstanceOf(ApiProblemException.class);
    }

    @Test
    void rotationAndExplicitRemovalReplaceTokenWithoutCreatingDevices() {
        registrations.register(user, session, installation, "token-two", true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.device_registration", Integer.class)).isEqualTo(1);
        registrations.register(user, session, installation, null, false);
        assertCleared();
    }

    @Test
    void revokedSessionClearsTokenImmediatelyAndCannotRegisterAgain() {
        jdbc.update("UPDATE app.auth_session SET revoked_at = ?, revoke_reason = 'logout' WHERE id = ?",
                java.sql.Timestamp.from(NOW), session);
        assertCleared();
        assertThatThrownBy(() -> registrations.register(user, session, installation, "new", true))
                .isInstanceOf(ApiProblemException.class);
        assertThat(deliveries.tick().sent()).isZero();
    }

    @Test
    void revokedDeviceClearsTokenImmediately() {
        jdbc.update("UPDATE app.device_registration SET invalidated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW), device);
        assertCleared();
    }

    @Test
    void accountDeletionPendingClearsTokensImmediately() {
        jdbc.update("UPDATE app.app_user SET status = 'deletion_pending', deletion_requested_at = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW), user);
        assertCleared();
    }

    @Test
    void expiredSessionIsPrunedByTick() {
        clock.now = NOW.plusSeconds(172801);
        deliveries.tick();
        assertCleared();
    }

    @Test
    void dueReminderSendsOnceAcrossRepeatedTicks() {
        assertThat(deliveries.tick().sent()).isEqualTo(1);
        assertThat(deliveries.tick().sent()).isZero();
        verify(sender, times(1)).send(org.mockito.ArgumentMatchers.eq("token-one"), any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.reminder_delivery WHERE status = 'sent'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void editedEventCancelsPreparedDeliveryAndReschedules() {
        records.prepare(100, clock.now);
        rooms.update(new com.hyped.app.identity.domain.UserId(user), room, 1, new RoomService.UpdateRoomCommand(
                null, LocalDate.of(2030, 12, 21), LocalTime.of(10, 0), "UTC", null, false, null, false));
        assertThat(deliveries.tick().sent()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.reminder_delivery WHERE status = 'canceled'",
                Integer.class)).isEqualTo(1);
        clock.now = NOW.plusSeconds(86400);
        assertThat(deliveries.tick().sent()).isEqualTo(1);
    }

    @Test
    void archivedRoomCancelsPreparedDelivery() {
        records.prepare(100, clock.now);
        rooms.archive(new com.hyped.app.identity.domain.UserId(user), room);
        assertThat(deliveries.tick().sent()).isZero();
        assertThat(count("canceled")).isEqualTo(1);
    }

    @Test
    void deletedRoomCascadesDeliveryRecords() {
        records.prepare(100, clock.now);
        jdbc.update("DELETE FROM app.room WHERE id = ?", room.value());
        assertThat(deliveries.tick().sent()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.reminder_delivery", Integer.class)).isZero();
    }

    @Test
    void removedMembershipCancelsPreparedDelivery() {
        UUID owner = UUID.randomUUID();
        var transactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO app.app_user(id,status,created_at,updated_at) VALUES (?,'active',?,?)",
                    owner, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
            jdbc.update("UPDATE app.room_member SET role = 'co_host' WHERE room_id = ? AND user_id = ?",
                    room.value(), user);
            jdbc.update("""
                    INSERT INTO app.room_member(room_id,user_id,role,joined_via,joined_at,updated_at)
                    VALUES (?,?,'owner','created',?,?)
                    """, room.value(), owner, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
            jdbc.update("UPDATE app.room SET owner_user_id = ?, member_count = 2 WHERE id = ?", owner, room.value());
        });
        records.prepare(100, clock.now);
        transactions.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM app.room_member WHERE room_id = ? AND user_id = ?", room.value(), user);
            jdbc.update("UPDATE app.room SET member_count = 1 WHERE id = ?", room.value());
        });
        assertThat(deliveries.tick().sent()).isZero();
        assertThat(count("canceled")).isEqualTo(1);
    }

    @Test
    void disabledPreferenceCancelsPreparedDelivery() {
        records.prepare(100, clock.now);
        jdbc.update("UPDATE app.room_reminder_preference SET remind_24_hours_before = FALSE WHERE room_id = ?",
                room.value());
        assertThat(deliveries.tick().sent()).isZero();
        assertThat(count("canceled")).isEqualTo(1);
    }

    @Test
    void expiredFcmTokenIsRemovedAndNotRetried() {
        when(sender.send(anyString(), any())).thenReturn(PushSender.Outcome.INVALID_TOKEN);
        assertThat(deliveries.tick().failed()).isEqualTo(1);
        assertCleared();
        assertThat(deliveries.tick().sent()).isZero();
    }

    @Test
    void transientFailureRetriesWithSameDurableNotificationId() {
        when(sender.send(anyString(), any())).thenReturn(PushSender.Outcome.RETRY, PushSender.Outcome.SENT);
        deliveries.tick();
        UUID id = jdbc.queryForObject("SELECT id FROM app.reminder_delivery", UUID.class);
        assertThat(deliveries.tick().sent()).isZero();
        clock.now = NOW.plusSeconds(60);
        assertThat(deliveries.tick().sent()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id FROM app.reminder_delivery", UUID.class)).isEqualTo(id);
        assertThat(count("sent")).isEqualTo(1);
    }

    @Test
    void pastLeadTimesDoNotCreateCatchupNotifications() {
        clock.now = NOW.plusSeconds(3601);
        RoomId near = rooms.create(new com.hyped.app.identity.domain.UserId(user), new RoomService.CreateRoomCommand(
                "Soon", LocalDate.of(2030, 12, 19), LocalTime.NOON, "UTC", null, null)).room().id();
        deliveries.tick();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.reminder_delivery WHERE room_id = ?",
                Integer.class, near.value())).isZero();
    }

    @Test
    void fixedOffsetsUseInstantsAcrossDstAndExactBoundary() {
        Instant event = java.time.ZonedDateTime.of(2030, 3, 10, 3, 30, 0, 0,
                ZoneId.of("America/New_York")).toInstant();
        assertThat(RoomReminder.Kind.DAY_BEFORE.scheduledAt(event)).isEqualTo(event.minusSeconds(86400));
        assertThat(RoomReminder.Kind.HOUR_BEFORE.scheduledAt(event)).isEqualTo(event.minusSeconds(3600));
        assertThat(RoomReminder.Kind.EVENT_TIME.scheduledAt(event)).isEqualTo(event);
        clock.now = NOW.minusMillis(1);
        assertThat(deliveries.tick().sent()).isZero();
        clock.now = NOW;
        assertThat(deliveries.tick().sent()).isEqualTo(1);
    }

    @Test
    void staleInvalidTokenFailureCannotEraseReplacement() {
        records.prepare(100, clock.now);
        var delivery = records.claim(100, clock.now).getFirst();
        registrations.register(user, session, installation, "token-replaced", true);
        assertThat(records.stillEligible(delivery, clock.now)).isFalse();
        records.finish(delivery, PushSender.Outcome.INVALID_TOKEN, clock.now);
        assertThat(jdbc.queryForObject("SELECT notifications_enabled FROM app.device_registration WHERE id = ?",
                Boolean.class, device)).isTrue();
    }

    @Test
    void recoveringExpiredClaimUsesStableIdAndRejectsOldAcknowledgement() {
        records.prepare(100, clock.now);
        var first = records.claim(100, clock.now).getFirst();
        assertThat(records.claim(100, clock.now)).isEmpty();
        clock.now = NOW.plusSeconds(301);
        var recovered = records.claim(100, clock.now).getFirst();
        assertThat(recovered.id()).isEqualTo(first.id());
        assertThat(recovered.claimToken()).isNotEqualTo(first.claimToken());
        records.finish(first, PushSender.Outcome.SENT, clock.now);
        assertThat(count("sending")).isEqualTo(1);
        records.finish(recovered, PushSender.Outcome.SENT, clock.now);
        assertThat(count("sent")).isEqualTo(1);
    }

    @Test
    void editingAwayAndBackCannotRedeliverSameOccurrence() {
        deliveries.tick();
        var actor = new com.hyped.app.identity.domain.UserId(user);
        rooms.update(actor, room, 1, new RoomService.UpdateRoomCommand(null, LocalDate.of(2030, 12, 21),
                LocalTime.of(10, 0), "UTC", null, false, null, false));
        rooms.update(actor, room, 2, new RoomService.UpdateRoomCommand(null, LocalDate.of(2030, 12, 20),
                LocalTime.of(10, 0), "UTC", null, false, null, false));
        deliveries.tick();
        verify(sender, times(1)).send(anyString(), any());
    }

    @Test
    void revokedSessionAfterClaimIsNotEligibleToSend() {
        records.prepare(100, clock.now);
        var delivery = records.claim(100, clock.now).getFirst();
        jdbc.update("UPDATE app.auth_session SET revoked_at = ?, revoke_reason = 'device_removed' WHERE id = ?",
                java.sql.Timestamp.from(NOW), session);
        assertThat(records.stillEligible(delivery, clock.now)).isFalse();
    }

    @Test
    void allThreeFixedBoundariesAreDeliveredOnlyWhenDue() {
        assertThat(deliveries.tick().sent()).isEqualTo(1);
        clock.now = Instant.parse("2030-12-20T09:00:00Z").minusMillis(1);
        assertThat(deliveries.tick().sent()).isZero();
        clock.now = clock.now.plusMillis(1);
        assertThat(deliveries.tick().sent()).isEqualTo(1);
        clock.now = Instant.parse("2030-12-20T10:00:00Z").minusMillis(1);
        assertThat(deliveries.tick().sent()).isZero();
        clock.now = clock.now.plusMillis(1);
        assertThat(deliveries.tick().sent()).isEqualTo(1);
        verify(sender, times(3)).send(anyString(), any());
    }

    @Test
    void deletingSessionRemovesPushTokenBeforeForeignKeyCleanup() {
        jdbc.update("DELETE FROM app.auth_session WHERE id = ?", session);
        assertCleared();
    }

    @Test
    void concurrentWorkerTicksCannotDeliverOccurrenceTwice() throws Exception {
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                return deliveries.tick();
            });
            var second = executor.submit(() -> {
                start.await();
                return deliveries.tick();
            });
            start.countDown();
            assertThat(first.get(20, java.util.concurrent.TimeUnit.SECONDS).sent()
                    + second.get(20, java.util.concurrent.TimeUnit.SECONDS).sent()).isEqualTo(1);
        }
        verify(sender, times(1)).send(anyString(), any());
    }

    @Test
    void enablingPastPreferenceDoesNotCreateCatchupDelivery() {
        clock.now = NOW.plusSeconds(1);
        jdbc.update("UPDATE app.room_reminder_preference SET updated_at = ? WHERE room_id = ?",
                java.sql.Timestamp.from(clock.now), room.value());
        assertThat(deliveries.tick().sent()).isZero();
    }

    private int count(String state) {
        return jdbc.queryForObject("SELECT count(*) FROM app.reminder_delivery WHERE status = ?", Integer.class, state);
    }

    private void assertCleared() {
        assertThat(jdbc.queryForObject("SELECT fcm_token_ciphertext FROM app.device_registration WHERE id = ?",
                byte[].class, device)).isNull();
        assertThat(jdbc.queryForObject("SELECT notifications_enabled FROM app.device_registration WHERE id = ?",
                Boolean.class, device)).isFalse();
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock pushClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        Instant now = NOW;
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
        @Override
        public Instant instant() {
            return now;
        }
    }
}
