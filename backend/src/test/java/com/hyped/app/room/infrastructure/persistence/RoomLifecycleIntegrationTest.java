package com.hyped.app.room.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.room.application.RoomOperationException;
import com.hyped.app.room.application.RoomService;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.RoomId;
import com.hyped.app.room.domain.RoomStatus;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Import(RoomLifecycleIntegrationTest.Config.class)
@Transactional
class RoomLifecycleIntegrationTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired
    RoomService rooms;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MutableClock clock;
    UserId owner;
    UserId member;
    RoomId room;

    @BeforeEach
    void setup() {
        clock.now = NOW;
        owner = user();
        member = user();
        room = create(owner);
        rooms.addMember(room, member);
    }

    @Test
    void memberLeaveIsIdempotentAndClearsPreferencesAndAccess() {
        rooms.leave(member, room, 1L);
        rooms.leave(member, room, 1L);
        assertThat(rooms.get(owner, room).room().memberCount()).isEqualTo(1);
        assertThat(rooms.get(owner, room).room().revision()).isEqualTo(2);
        assertThatThrownBy(() -> rooms.get(member, room)).isInstanceOf(RoomOperationException.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM app.room_reminder_preference WHERE room_id = ? AND user_id = ?
                """, Integer.class, room.value(), member.value())).isZero();
    }

    @Test
    void coHostCanLeaveButOwnerMustTransferFirst() {
        rooms.changeRole(owner, room, member, MembershipRole.CO_HOST);
        assertThatThrownBy(() -> rooms.leave(owner, room, null)).isInstanceOf(RoomOperationException.class)
                .extracting("code").isEqualTo("OWNERSHIP_TRANSFER_REQUIRED");
        rooms.leave(member, room, 2L);
        assertThat(rooms.get(owner, room).room().memberCount()).isEqualTo(1);
    }

    @Test
    void previousOwnerCanLeaveAfterOwnershipTransfer() {
        rooms.transfer(owner, room, member, 1L);
        rooms.leave(owner, room, 2L);
        assertThat(rooms.get(member, room).role()).isEqualTo(MembershipRole.OWNER);
        assertThat(rooms.get(member, room).room().memberCount()).isEqualTo(1);
    }

    @Test
    void foreignIdsCannotBeUsedToMutateLifecycle() {
        UserId stranger = user();
        rooms.leave(stranger, room, null);
        rooms.deletePermanently(stranger, room, null);
        assertThatThrownBy(() -> rooms.archive(stranger, room)).isInstanceOf(RoomOperationException.class);
        assertThatThrownBy(() -> rooms.restore(stranger, room, null)).isInstanceOf(RoomOperationException.class);
        assertThat(rooms.get(owner, room).room().status()).isEqualTo(RoomStatus.ACTIVE);
    }

    @Test
    void onlyOwnerMayArchiveRestoreAndDelete() {
        rooms.changeRole(owner, room, member, MembershipRole.CO_HOST);
        assertThatThrownBy(() -> rooms.archive(member, room)).isInstanceOf(RoomOperationException.class);
        rooms.archive(owner, room);
        assertThatThrownBy(() -> rooms.restore(member, room, null)).isInstanceOf(RoomOperationException.class);
        clock.now = NOW.plusSeconds(86400);
        assertThatThrownBy(() -> rooms.deletePermanently(member, room, null))
                .isInstanceOf(RoomOperationException.class);
    }

    @Test
    void archiveRetryPreservesRecoveryDeadlineAndStopsRemindersAndJoins() {
        var archived = rooms.archive(owner, room, 1L);
        clock.now = NOW.plusSeconds(3600);
        assertThat(rooms.archive(owner, room, 1L)).isEqualTo(archived);
        assertThat(archived.room().deleteAfter()).isEqualTo(NOW.plusSeconds(86400));
        assertThat(archived.room().revision()).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM app.room_reminder WHERE room_id = ? AND status IN ('pending', 'claimed')
                """, Integer.class, room.value())).isZero();
        assertThatThrownBy(() -> rooms.addMember(room, user())).isInstanceOf(RoomOperationException.class)
                .extracting("code").isEqualTo("ROOM_ARCHIVED");
    }

    @Test
    void participantsCanLeaveArchivedRooms() {
        rooms.archive(owner, room);
        rooms.leave(member, room, 2L);
        assertThat(rooms.get(owner, room).room().memberCount()).isEqualTo(1);
    }

    @Test
    void restoreBeforeDeadlineIsIdempotentAndRebuildsOnlyCurrentFixedReminders() {
        rooms.archive(owner, room);
        clock.now = NOW.plusSeconds(86399);
        var restored = rooms.restore(owner, room, 2L);
        assertThat(restored.room().status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(restored.room().deleteAfter()).isNull();
        assertThat(restored.room().archivedAt()).isNull();
        assertThat(restored.room().revision()).isEqualTo(3);
        assertThat(rooms.restore(owner, room, 2L)).isEqualTo(restored);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM app.room_reminder WHERE room_id = ? AND status = 'pending'
                """, Integer.class, room.value())).isEqualTo(3);
        rooms.addMember(room, user());
        assertThat(rooms.get(owner, room).room().memberCount()).isEqualTo(3);
    }

    @Test
    void recoveryExpiresAtExactEqualityAndDeletionBecomesEligible() {
        rooms.archive(owner, room);
        clock.now = NOW.plusSeconds(86400);
        assertThatThrownBy(() -> rooms.restore(owner, room, 2L)).isInstanceOf(RoomOperationException.class)
                .extracting("code").isEqualTo("ROOM_RECOVERY_EXPIRED");
        rooms.deletePermanently(owner, room, 2L);
        rooms.deletePermanently(owner, room, 2L);
        assertThatThrownBy(() -> rooms.get(owner, room)).isInstanceOf(RoomOperationException.class);
    }

    @Test
    void activeAndNotYetEligibleArchivedRoomsCannotBeDeleted() {
        assertThatThrownBy(() -> rooms.deletePermanently(owner, room, 1L))
                .isInstanceOf(RoomOperationException.class).extracting("code").isEqualTo("ROOM_DELETE_NOT_ELIGIBLE");
        rooms.archive(owner, room);
        clock.now = NOW.plusSeconds(86400).minusNanos(1);
        assertThatThrownBy(() -> rooms.deletePermanently(owner, room, 2L))
                .isInstanceOf(RoomOperationException.class).extracting("code").isEqualTo("ROOM_DELETE_NOT_ELIGIBLE");
    }

    @Test
    void lifecycleMutationsRejectStaleRevisionWithoutPartialChanges() {
        assertThatThrownBy(() -> rooms.leave(member, room, 9L)).isInstanceOf(RoomOperationException.class)
                .extracting("code").isEqualTo("ROOM_REVISION_MISMATCH");
        assertThatThrownBy(() -> rooms.archive(owner, room, 9L)).isInstanceOf(RoomOperationException.class)
                .extracting("code").isEqualTo("ROOM_REVISION_MISMATCH");
        rooms.archive(owner, room, 1L);
        assertThatThrownBy(() -> rooms.restore(owner, room, 1L)).isInstanceOf(RoomOperationException.class)
                .extracting("code").isEqualTo("ROOM_REVISION_MISMATCH");
        clock.now = NOW.plusSeconds(86400);
        assertThatThrownBy(() -> rooms.deletePermanently(owner, room, 1L)).isInstanceOf(RoomOperationException.class)
                .extracting("code").isEqualTo("ROOM_REVISION_MISMATCH");
    }

    @Test
    void restoreRechecksActiveOwnedLimit() {
        rooms.archive(owner, room);
        for (int index = 0; index < 10; index++) {
            create(owner);
        }
        assertThatThrownBy(() -> rooms.restore(owner, room, 2L)).isInstanceOf(RoomOperationException.class)
                .extracting("code").isEqualTo("OWNED_ROOM_LIMIT_REACHED");
        assertThat(rooms.get(owner, room).room().status()).isEqualTo(RoomStatus.ARCHIVED);
    }

    @Test
    void restoreRechecksEveryMembersActiveJoinedLimit() {
        rooms.archive(owner, room);
        for (int index = 0; index < 25; index++) {
            rooms.addMember(create(user()), member);
        }
        assertThatThrownBy(() -> rooms.restore(owner, room, 2L)).isInstanceOf(RoomOperationException.class)
                .extracting("code").isEqualTo("JOINED_ROOM_LIMIT_REACHED");
    }

    @Test
    void deletionCascadesAllCurrentRoomDataAndClearsProtectedReplays() {
        jdbc.update("""
                INSERT INTO app.room_invitation(room_id,generation,link_token_hash,room_code_hmac,
                    link_token_ciphertext,room_code_ciphertext,created_by_user_id,created_at,updated_at)
                VALUES (?,1,?,?,?, ?,?,?,?)
                """, room.value(), new byte[32], new byte[32], new byte[] {1}, new byte[] {2}, owner.value(),
                Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO ops.idempotency_record(user_id,idempotency_key,operation,request_hash,resource_id,
                    expires_at,created_at) VALUES (?,'key','join',?,?,?,?)
                """, owner.value(), new byte[32], room.value(), Timestamp.from(NOW.plusSeconds(172800)),
                Timestamp.from(NOW));
        rooms.archive(owner, room);
        clock.now = NOW.plusSeconds(86400);
        rooms.deletePermanently(owner, room, 2L);
        for (String table : List.of("room_member", "room_invitation", "room_theme", "room_reminder",
                "room_reminder_preference", "reminder_delivery")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app." + table + " WHERE room_id = ?",
                    Integer.class, room.value())).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ops.idempotency_record WHERE resource_id = ?",
                Integer.class, room.value())).isZero();
    }

    @Test
    void cleanupTickDeletesOnlyEligibleArchivesAndIsRepeatable() {
        RoomId active = create(owner);
        rooms.archive(owner, room);
        clock.now = NOW.plusSeconds(86399);
        assertThat(rooms.purgeEligible()).isZero();
        clock.now = NOW.plusSeconds(86400);
        assertThat(rooms.purgeEligible()).isEqualTo(1);
        assertThat(rooms.purgeEligible()).isZero();
        assertThat(rooms.get(owner, active).room().status()).isEqualTo(RoomStatus.ACTIVE);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void competingRestoresDoNotDuplicateRevisionOrReminders() throws Exception {
        rooms.archive(owner, room);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                return rooms.restore(owner, room, 2L);
            });
            var second = executor.submit(() -> {
                start.await();
                return rooms.restore(owner, room, 2L);
            });
            start.countDown();
            assertThat(first.get().room().revision()).isEqualTo(3);
            assertThat(second.get().room().revision()).isEqualTo(3);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM app.room_reminder WHERE room_id = ? AND status = 'pending'
                    """, Integer.class, room.value())).isEqualTo(3);
        } finally {
            jdbc.update("DELETE FROM app.room WHERE id = ?", room.value());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cleanupAndOwnerDeletionRaceSafely() throws Exception {
        rooms.archive(owner, room);
        clock.now = NOW.plusSeconds(86400);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                rooms.purgeEligible();
                return true;
            });
            var second = executor.submit(() -> {
                start.await();
                rooms.deletePermanently(owner, room, 2L);
                return true;
            });
            start.countDown();
            assertThat(first.get()).isTrue();
            assertThat(second.get()).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.room WHERE id = ?", Integer.class, room.value()))
                    .isZero();
        }
    }

    @Test
    void mixedRoomListKeepsActiveRoomsAheadOfArchivedHistory() {
        rooms.archive(owner, room);
        RoomId active = create(owner);
        assertThat(rooms.list(owner, null, 1).getFirst().room().id()).isEqualTo(active);
        assertThat(rooms.list(owner, RoomStatus.ARCHIVED, 50).getFirst().room().id()).isEqualTo(room);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void ownerLeaveRacingTransferCannotRemoveTheOnlyOwner() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var transfer = executor.submit(() -> {
                start.await();
                rooms.transfer(owner, room, member, 1L);
                return true;
            });
            var leave = executor.submit(() -> {
                start.await();
                try {
                    rooms.leave(owner, room, null);
                    return "left";
                } catch (RoomOperationException failure) {
                    return failure.code();
                }
            });
            start.countDown();
            assertThat(transfer.get()).isTrue();
            assertThat(leave.get()).isIn("left", "OWNERSHIP_TRANSFER_REQUIRED");
            assertThat(rooms.get(member, room).role()).isEqualTo(MembershipRole.OWNER);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.room_member WHERE room_id = ? AND role = 'owner'",
                    Integer.class, room.value())).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM app.room WHERE id = ?", room.value());
        }
    }

    private RoomId create(UserId actor) {
        return rooms.create(actor, new RoomService.CreateRoomCommand("Trip", LocalDate.of(2031, 1, 1),
                LocalTime.NOON, "UTC", null, null)).room().id();
    }

    private UserId user() {
        var id = new UserId(UUID.randomUUID());
        jdbc.update("INSERT INTO app.app_user(id,status,created_at,updated_at) VALUES (?,'active',?,?)", id.value(),
                Timestamp.from(clock.now), Timestamp.from(clock.now));
        com.hyped.app.policy.AcceptedPolicyFixture.accept(jdbc, id.value());
        return id;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        @Primary
        MutableClock lifecycleClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        volatile Instant now = NOW;
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
