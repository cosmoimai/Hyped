package com.hyped.app.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.moderation.application.ModerationService;
import com.hyped.app.moderation.application.SafetyException;
import com.hyped.app.moderation.application.SafetyService;
import com.hyped.app.moderation.application.port.out.SafetyStore;
import com.hyped.app.room.application.RoomOperationException;
import com.hyped.app.room.application.RoomService;
import com.hyped.app.room.application.port.out.RoomRepository;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.RoomId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@Testcontainers
@Import(SafetyIntegrationTest.CipherConfig.class)
class SafetyIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired SafetyService safety;
    @Autowired ModerationService moderation;
    @Autowired SafetyStore store;
    @Autowired RoomService rooms;
    @Autowired RoomRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @TestConfiguration
    static class CipherConfig {
        @Bean
        PersonalDataCipher reportCipher() {
            return new PersonalDataCipher() {
                private final byte[] key = new byte[32];

                @Override
                public byte[] encrypt(
                        UserId user, String reference, PersonalDataField field, byte[] value) {
                    return crypt(javax.crypto.Cipher.ENCRYPT_MODE, value);
                }

                @Override
                public byte[] decrypt(
                        UserId user, String reference, PersonalDataField field, byte[] value) {
                    return crypt(javax.crypto.Cipher.DECRYPT_MODE, value);
                }

                private byte[] crypt(int mode, byte[] value) {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isFalse();
                    try {
                        var cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                        cipher.init(
                                mode,
                                new javax.crypto.spec.SecretKeySpec(key, "AES"),
                                new javax.crypto.spec.GCMParameterSpec(128, new byte[12]));
                        return cipher.doFinal(value);
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
                }
            };
        }
    }

    UserId user() {
        var user = new UserId(UUID.randomUUID());
        jdbc.update(
                "INSERT INTO app.app_user(id,status,pii_key_reference,created_at,updated_at)"
                    + " VALUES(?,'active','fixture-reference',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                user.value());
        com.hyped.app.policy.AcceptedPolicyFixture.accept(jdbc, user.value());
        return user;
    }

    RoomId room(UserId owner) {
        return rooms.create(
                        owner,
                        new RoomService.CreateRoomCommand(
                                "Safe room",
                                LocalDate.of(2040, 1, 1),
                                LocalTime.NOON,
                                "UTC",
                                null,
                                null))
                .room()
                .id();
    }

    String key() {
        return UUID.randomUUID().toString();
    }

    SafetyService.ReportRequest request(RoomId room, String type, UserId target, String details) {
        return new SafetyService.ReportRequest(
                room.value(),
                type,
                target == null ? null : target.value(),
                "INAPPROPRIATE_CONTENT",
                details);
    }

    @Test
    void archivedMembershipsAreRemovedByBlockWithoutTouchingOwnerOrRecoveryDeadline() {
        var a = user();
        var b = user();
        var room = room(a);
        rooms.addMember(room, b);
        var archived = rooms.archive(a, room).room();
        safety.block(b, a.value(), room.value(), key());
        assertThat(repository.findAuthorized(room, b)).isEmpty();
        assertThat(repository.findAuthorized(room, a).orElseThrow().room().deleteAfter())
                .isEqualTo(archived.deleteAfter());
        assertThat(repository.findAuthorized(room, a).orElseThrow().room().memberCount())
                .isEqualTo(1);
    }

    @Test
    void reviewerRateIsBoundedAndIndependentAcrossIdentitiesAndWindows() {
        var now = java.time.Instant.parse("2030-01-01T00:00:00Z");
        byte[] reviewer = SafetyService.digest("fixture-reviewer-" + UUID.randomUUID());
        for (int i = 0; i < 60; i++) {
            assertThat(store.allowReview(reviewer, now)).isTrue();
        }
        assertThat(store.allowReview(reviewer, now)).isFalse();
        assertThat(store.allowReview(SafetyService.digest("other-reviewer"), now)).isTrue();
        assertThat(store.allowReview(reviewer, now.plusSeconds(60))).isTrue();
    }

    @Test
    void reportsAreEncryptedIdempotentAndRestrictedToCurrentContext() {
        var owner = user();
        var member = user();
        var outsider = user();
        var room = room(owner);
        rooms.addMember(room, member);
        String key = key();
        var report =
                safety.report(
                        member, key, request(room, "ACCOUNT", owner, "Private report detail"));
        assertThat(
                        safety.report(
                                member,
                                key,
                                request(room, "ACCOUNT", owner, "Private report detail")))
                .isEqualTo(report);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT details_ciphertext FROM app.report WHERE id=?",
                                byte[].class,
                                report.reportId()))
                .isNotEqualTo("Private report detail".getBytes());
        assertThat(
                        moderation.queue(100).stream()
                                .filter(item -> item.reportId().equals(report.reportId())))
                .singleElement()
                .extracting(ModerationService.QueueItem::details)
                .isEqualTo("Private report detail");
        assertThatThrownBy(() -> safety.report(outsider, key(), request(room, "ROOM", null, null)))
                .isInstanceOf(SafetyException.class)
                .hasMessageContaining("unavailable");
        assertThatThrownBy(
                        () ->
                                safety.report(
                                        member, key(), request(room, "ACCOUNT", outsider, null)))
                .isInstanceOf(SafetyException.class);
        assertThatThrownBy(() -> safety.report(member, key, request(room, "ROOM", null, null)))
                .isInstanceOf(SafetyException.class);
        rooms.leave(member, room, null);
        assertThatThrownBy(() -> safety.report(member, key(), request(room, "ROOM", null, null)))
                .isInstanceOf(SafetyException.class);
    }

    @Test
    void dailyReportLimitIgnoresIdempotentRetriesAndValidatesInputs() {
        var owner = user();
        var room = room(owner);
        String key = key();
        var r = request(room, "ROOM", null, null);
        safety.report(owner, key, r);
        for (int i = 0; i < 4; i++) {
            safety.report(owner, key(), r);
        }
        safety.report(owner, key, r);
        assertThatThrownBy(() -> safety.report(owner, key(), r))
                .satisfies(
                        e ->
                                assertThat(((SafetyException) e).code())
                                        .isEqualTo("REPORT_LIMIT_REACHED"));
        assertThatThrownBy(() -> safety.report(owner, null, r)).isInstanceOf(SafetyException.class);
        assertThatThrownBy(
                        () ->
                                safety.report(
                                        owner, key(), request(room, "ROOM", null, "x".repeat(501))))
                .isInstanceOf(SafetyException.class);
        assertThatThrownBy(() -> safety.report(owner, key(), request(room, "ROOM", owner, null)))
                .isInstanceOf(SafetyException.class);
    }

    @Test
    void blockAppliesSafeRoomRulesWithoutChangingThirdPartyOwnerOrCapacity() {
        var a = user();
        var b = user();
        var c = user();
        var owned = room(a);
        var other = room(b);
        var shared = room(c);
        rooms.addMember(owned, b);
        rooms.addMember(other, a);
        rooms.addMember(shared, a);
        rooms.addMember(shared, b);
        rooms.changeRole(c, shared, a, MembershipRole.CO_HOST);
        var result = safety.block(a, b.value(), shared.value(), key());
        assertThat(result.removedCount()).isEqualTo(1);
        assertThat(result.leftCount()).isEqualTo(1);
        assertThat(repository.findAuthorized(owned, b)).isEmpty();
        assertThat(repository.findAuthorized(other, a)).isEmpty();
        assertThat(repository.findAuthorized(shared, a)).isPresent();
        assertThat(repository.findAuthorized(shared, b)).isPresent();
        assertThat(repository.findAuthorized(shared, c).orElseThrow().room().memberCount())
                .isEqualTo(3);
        assertThat(rooms.members(a, shared).items())
                .anySatisfy(
                        m -> {
                            if (m.userId().equals(b.value())) {
                                assertThat(m.displayName()).isEqualTo("Blocked account");
                                assertThat(m.hiddenProfile()).isTrue();
                                assertThat(m.photo()).isNull();
                            }
                        });
        assertThat(store.blocked(a.value(), b.value())).isTrue();
        assertThat(safety.invalidations(a)).hasSize(3);
        assertThat(safety.blocks(b, null).items()).isEmpty();
        assertThat(safety.blocks(a, null).items())
                .extracting(SafetyService.BlockView::userId)
                .containsExactly(b.value());
        assertThatThrownBy(() -> rooms.removeMember(a, shared, b, null))
                .isInstanceOf(RoomOperationException.class);
        assertThatThrownBy(() -> rooms.transfer(c, shared, b, null))
                .isInstanceOf(RoomOperationException.class);
        assertThatThrownBy(() -> rooms.addMember(owned, b))
                .isInstanceOf(RoomOperationException.class);
        assertThatThrownBy(() -> rooms.addMember(other, a))
                .isInstanceOf(RoomOperationException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM app.room_reminder_preference WHERE room_id=?"
                                        + " AND user_id=?",
                                Integer.class,
                                owned.value(),
                                b.value()))
                .isZero();
    }

    @Test
    void retriesAndReciprocalUnblockingNeverRestoreMembershipsOrRevealReverseBlocks() {
        var a = user();
        var b = user();
        var c = user();
        var owned = room(a);
        var shared = room(c);
        rooms.addMember(owned, b);
        rooms.addMember(shared, a);
        rooms.addMember(shared, b);
        String key = key();
        var result = safety.block(a, b.value(), shared.value(), key);
        safety.block(b, a.value(), shared.value(), key());
        safety.unblock(a, b.value());
        safety.unblock(a, b.value());
        assertThat(safety.block(a, b.value(), shared.value(), key)).isEqualTo(result);
        assertThat(store.ownBlock(a.value(), b.value())).isFalse();
        assertThat(store.blocked(a.value(), b.value())).isTrue();
        assertThat(repository.findAuthorized(owned, b)).isEmpty();
        safety.unblock(b, a.value());
        assertThat(store.blocked(a.value(), b.value())).isFalse();
        assertThat(repository.findAuthorized(owned, b)).isEmpty();
        assertThat(rooms.members(a, shared).items())
                .noneMatch(RoomService.MemberView::hiddenProfile);
    }

    @Test
    void foreignAndSelfBlocksFailWithoutCreatingRecords() {
        var a = user();
        var b = user();
        var room = room(a);
        assertThatThrownBy(() -> safety.block(a, b.value(), room.value(), key()))
                .isInstanceOf(SafetyException.class);
        assertThatThrownBy(() -> safety.block(a, a.value(), room.value(), key()))
                .isInstanceOf(SafetyException.class);
        assertThat(safety.blocks(a, null).items()).isEmpty();
        safety.unblock(a, b.value()); // A missing directional block remains safely idempotent.
    }

    @Test
    void concurrentReciprocalBlocksPreserveExactlyOneOwner() throws Exception {
        var a = user();
        var b = user();
        var room = room(a);
        rooms.addMember(room, b);
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first =
                    pool.submit(
                            () -> {
                                gate.await();
                                try {
                                    safety.block(a, b.value(), room.value(), key());
                                } catch (SafetyException ignored) {
                                }
                                return true;
                            });
            var second =
                    pool.submit(
                            () -> {
                                gate.await();
                                try {
                                    safety.block(b, a.value(), room.value(), key());
                                } catch (SafetyException ignored) {
                                }
                                return true;
                            });
            gate.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM app.room_member WHERE room_id=? AND"
                                        + " role='owner'",
                                Integer.class,
                                room.value()))
                .isEqualTo(1);
        assertThat(repository.findAuthorized(room, a).orElseThrow().room().memberCount())
                .isEqualTo(1);
    }

    @Test
    void blockAndOwnershipTransferSerializeWithoutOwnerLoss() throws Exception {
        var a = user();
        var b = user();
        var room = room(a);
        rooms.addMember(room, b);
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var block =
                    pool.submit(
                            () -> {
                                gate.await();
                                safety.block(a, b.value(), room.value(), key());
                                return true;
                            });
            var transfer =
                    pool.submit(
                            () -> {
                                gate.await();
                                try {
                                    rooms.transfer(a, room, b);
                                } catch (RoomOperationException ignored) {
                                }
                                return true;
                            });
            gate.countDown();
            block.get(10, TimeUnit.SECONDS);
            transfer.get(10, TimeUnit.SECONDS);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM app.room_member WHERE room_id=? AND"
                                        + " role='owner'",
                                Integer.class,
                                room.value()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT member_count FROM app.room WHERE id=?",
                                Integer.class,
                                room.value()))
                .isEqualTo(1);
    }

    @Test
    void versionedAcknowledgmentCannotDropNewerInvalidationOrAnotherAccountsWork() {
        var a = user();
        var b = user();
        var room = room(a);
        rooms.addMember(room, b);
        safety.block(a, b.value(), room.value(), key());
        var before = safety.invalidations(a).getFirst();
        safety.acknowledge(b, before.version());
        assertThat(safety.invalidations(a)).hasSize(1);
        store.invalidate(a.value(), room.value(), java.time.Instant.now());
        safety.acknowledge(a, before.version());
        assertThat(safety.invalidations(a)).hasSize(1);
        safety.acknowledge(a, safety.invalidations(a).getFirst().version());
        assertThat(safety.invalidations(a)).isEmpty();
    }

    @Test
    void moderationArchivesWithRevisionChecksAndRequiresReviewToRestore() {
        var owner = user();
        var room = room(owner);
        var report = safety.report(owner, key(), request(room, "ROOM", null, null));
        assertThatThrownBy(
                        () -> moderation.decide(report.reportId(), "ARCHIVE_ROOM", 99L, "reviewer"))
                .isInstanceOf(SafetyException.class);
        moderation.decide(report.reportId(), "ARCHIVE_ROOM", 1L, "reviewer");
        moderation.decide(report.reportId(), "ARCHIVE_ROOM", 1L, "reviewer");
        assertThat(store.held(room.value())).isTrue();
        assertThat(rooms.get(owner, room).moderationHold()).isTrue();
        assertThatThrownBy(() -> rooms.restore(owner, room, null))
                .isInstanceOf(RoomOperationException.class);
        moderation.decide(report.reportId(), "RELEASE_ROOM", null, "reviewer");
        rooms.restore(owner, room, null);
        assertThat(repository.findAuthorized(room, owner).orElseThrow().room().status())
                .isEqualTo(com.hyped.app.room.domain.RoomStatus.ACTIVE);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM ops.moderation_audit WHERE report_id=?",
                                Integer.class,
                                report.reportId()))
                .isEqualTo(2);
    }

    @Test
    void dismissalSuspensionRetentionAndDeletionPseudonymizationAreSafe() {
        var a = user();
        var b = user();
        var room = room(a);
        rooms.addMember(room, b);
        var dismissed = safety.report(a, key(), request(room, "THEME", null, null));
        moderation.decide(dismissed.reportId(), "DISMISS", null, "reviewer");
        var report = safety.report(a, key(), request(room, "ACCOUNT", b, "Private detail"));
        moderation.decide(report.reportId(), "SUSPEND_ACCOUNT", null, "reviewer");
        assertThat(store.active(b.value())).isFalse();
        assertThatThrownBy(() -> moderation.decide(report.reportId(), "DISMISS", null, "reviewer"))
                .isInstanceOf(SafetyException.class);
        jdbc.update(
                "UPDATE app.report SET retain_until=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE"
                        + " id=?",
                dismissed.reportId());
        safety.cleanup();
        assertThat(store.report(dismissed.reportId(), false)).isNull();
        assertThat(store.report(report.reportId(), false)).isNotNull();
    }
}
