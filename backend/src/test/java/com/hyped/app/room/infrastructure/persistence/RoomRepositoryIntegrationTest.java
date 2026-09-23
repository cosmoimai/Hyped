package com.hyped.app.room.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.room.application.RoomOperationException;
import com.hyped.app.room.application.RoomService;
import com.hyped.app.room.application.RoomService.CreateRoomCommand;
import com.hyped.app.room.application.RoomService.UpdateRoomCommand;
import com.hyped.app.room.application.port.out.RoomRepository;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.RoomId;
import com.hyped.app.room.domain.RoomStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class RoomRepositoryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private RoomService service;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationCreatesConstrainedRoomTables() {
        assertThat(jdbc.queryForObject(
                "SELECT success FROM flyway.flyway_schema_history WHERE version = '5'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'app' AND table_name IN ('room', 'room_member')
                """, String.class)).containsExactlyInAnyOrder("room", "room_member");
    }

    @Test
    void validatesRoomFieldsAndLocalEventTime() {
        UserId owner = user();
        assertThatThrownBy(() -> service.create(owner, new CreateRoomCommand(" ", LocalDate.of(2030, 1, 1),
                LocalTime.NOON, "UTC", null, null)))
                .isInstanceOf(RoomOperationException.class)
                .extracting(exception -> ((RoomOperationException) exception).code())
                .isEqualTo("VALIDATION_FAILED");
        assertThatThrownBy(() -> service.create(owner, new CreateRoomCommand("Title", LocalDate.of(2020, 1, 1),
                LocalTime.NOON, "UTC", null, null)))
                .isInstanceOf(RoomOperationException.class)
                .extracting(exception -> ((RoomOperationException) exception).code())
                .isEqualTo("EVENT_TIME_NOT_FUTURE");
        assertThatThrownBy(() -> service.create(owner, new CreateRoomCommand("Title", LocalDate.of(2030, 3, 31),
                LocalTime.of(2, 30), "Europe/Berlin", null, null)))
                .isInstanceOf(RoomOperationException.class)
                .extracting(exception -> ((RoomOperationException) exception).code())
                .isEqualTo("EVENT_TIME_NONEXISTENT");
        assertThatThrownBy(() -> service.create(owner, new CreateRoomCommand("Title", LocalDate.of(2030, 10, 27),
                LocalTime.of(2, 30), "Europe/Berlin", null, null)))
                .isInstanceOf(RoomOperationException.class)
                .extracting(exception -> ((RoomOperationException) exception).code())
                .isEqualTo("EVENT_TIME_AMBIGUOUS");
    }

    @Test
    void createsAndListsOnlyCallerRoomsWithTimestampAndTimezoneRoundTrip() {
        UserId owner = user();
        UserId outsider = user();
        var created = service.create(owner, command("Asia/Kolkata"));

        assertThat(created.room().eventAt()).isEqualTo("2030-12-20T04:30:00Z");
        assertThat(created.room().eventTimeZone()).isEqualTo("Asia/Kolkata");
        assertThat(created.role()).isEqualTo(MembershipRole.OWNER);
        assertThat(service.list(owner, RoomStatus.ACTIVE, 20)).containsExactly(created);
        assertThat(service.list(outsider, RoomStatus.ACTIVE, 20)).isEmpty();
    }

    @Test
    void conditionalUpdateIncrementsRevisionAndRejectsStaleWriter() {
        UserId owner = user();
        RoomId roomId = service.create(owner, command("UTC")).room().id();
        UpdateRoomCommand update = new UpdateRoomCommand("Updated", null, null, null,
                "Goa", true, null, false);

        var changed = service.update(owner, roomId, 1, update);

        assertThat(changed.room().revision()).isEqualTo(2);
        assertThat(changed.room().location()).isEqualTo("Goa");
        assertThatThrownBy(() -> service.update(owner, roomId, 1, update))
                .isInstanceOf(RoomOperationException.class)
                .extracting(exception -> ((RoomOperationException) exception).code())
                .isEqualTo("ROOM_REVISION_MISMATCH");
    }

    @Test
    void archiveIsIdempotentAndBlocksMutationForTwentyFourHourWindow() {
        UserId owner = user();
        RoomId roomId = service.create(owner, command("UTC")).room().id();

        var first = service.archive(owner, roomId);
        var second = service.archive(owner, roomId);

        assertThat(second).isEqualTo(first);
        assertThat(first.room().deleteAfter()).isEqualTo(first.room().archivedAt().plusSeconds(86_400));
        assertThatThrownBy(() -> service.update(owner, roomId, 1,
                new UpdateRoomCommand("No", null, null, null, null, false, null, false)))
                .isInstanceOf(RoomOperationException.class)
                .extracting(exception -> ((RoomOperationException) exception).code())
                .isEqualTo("ROOM_ARCHIVED");
    }

    @Test
    void ownershipTransferChangesBothRolesAndOwnerAtomically() {
        UserId owner = user();
        UserId member = user();
        RoomId roomId = service.create(owner, command("UTC")).room().id();
        addMember(roomId, member, "member");

        var result = service.transfer(owner, roomId, member);

        assertThat(result.room().room().ownerUserId()).isEqualTo(member);
        assertThat(role(roomId, owner)).isEqualTo("co_host");
        assertThat(role(roomId, member)).isEqualTo("owner");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM app.room_member WHERE room_id = ? AND role = 'owner'",
                Integer.class, roomId.value())).isEqualTo(1);
    }

    @Test
    void ownerCoHostAndMemberAuthorizationMatrixIsEnforced() {
        UserId owner = user();
        UserId member = user();
        RoomId roomId = service.create(owner, command("UTC")).room().id();
        service.addMember(roomId, member);

        service.changeRole(owner, roomId, member, MembershipRole.CO_HOST);
        assertThat(service.update(member, roomId, 1,
                new UpdateRoomCommand("Co-host edit", null, null, null, null, false, null, false))
                .room().revision()).isEqualTo(2);
        service.changeRole(owner, roomId, member, MembershipRole.MEMBER);
        assertThatThrownBy(() -> service.update(member, roomId, 2,
                new UpdateRoomCommand("Member edit", null, null, null, null, false, null, false)))
                .isInstanceOf(RoomOperationException.class)
                .extracting(exception -> ((RoomOperationException) exception).code())
                .isEqualTo("ROOM_ACTION_FORBIDDEN");
        assertThatThrownBy(() -> service.changeRole(member, roomId, owner, MembershipRole.MEMBER))
                .isInstanceOf(RoomOperationException.class);
    }

    @Test
    void duplicateMembershipIsRejectedByDatabase() {
        UserId owner = user();
        UserId member = user();
        RoomId roomId = service.create(owner, command("UTC")).room().id();
        addMember(roomId, member, "member");

        assertThatThrownBy(() -> addMember(roomId, member, "member"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void secondOwnerIsRejectedByDatabase() {
        UserId owner = user();
        RoomId roomId = service.create(owner, command("UTC")).room().id();

        assertThatThrownBy(() -> addMember(roomId, user(), "owner"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deferredConsistencyConstraintRejectsRemovingTheOnlyOwnerRole() {
        UserId owner = user();
        RoomId roomId = service.create(owner, command("UTC")).room().id();

        assertThatThrownBy(() -> {
            jdbc.update("UPDATE app.room_member SET role = 'co_host' WHERE room_id = ? AND user_id = ?",
                    roomId.value(), owner.value());
            jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsMemberCountBeyondTwentyFive() {
        UserId owner = user();
        RoomId roomId = service.create(owner, command("UTC")).room().id();

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE app.room SET member_count = 26 WHERE id = ?", roomId.value()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ownershipTransferRollsBackWhenTargetIsNotMember() {
        UserId owner = user();
        UserId outsider = user();
        RoomId roomId = service.create(owner, command("UTC")).room().id();

        assertThatThrownBy(() -> service.transfer(owner, roomId, outsider))
                .isInstanceOf(RoomOperationException.class);
        assertThat(rooms.findAuthorized(roomId, owner).orElseThrow().role()).isEqualTo(MembershipRole.OWNER);
    }

    @Test
    void ownedRoomLimitStopsTheEleventhActiveRoom() {
        UserId owner = user();
        for (int index = 0; index < RoomService.MAX_OWNED_ROOMS; index++) {
            service.create(owner, command("UTC"));
        }

        assertThatThrownBy(() -> service.create(owner, command("UTC")))
                .isInstanceOf(RoomOperationException.class)
                .extracting(exception -> ((RoomOperationException) exception).code())
                .isEqualTo("OWNED_ROOM_LIMIT_REACHED");
    }

    @Test
    void joinedRoomLimitStopsTheTwentySixthMembership() {
        UserId member = user();
        for (int index = 0; index < RoomService.MAX_JOINED_ROOMS; index++) {
            RoomId roomId = service.create(user(), command("UTC")).room().id();
            service.addMember(roomId, member);
        }
        RoomId extra = service.create(user(), command("UTC")).room().id();

        assertThatThrownBy(() -> service.addMember(extra, member))
                .isInstanceOf(RoomOperationException.class)
                .extracting(exception -> ((RoomOperationException) exception).code())
                .isEqualTo("JOINED_ROOM_LIMIT_REACHED");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentMembershipAttemptsCannotExceedTwentyFive() throws Exception {
        UserId owner = user();
        RoomId roomId = service.create(owner, command("UTC")).room().id();
        for (int index = 0; index < 23; index++) {
            service.addMember(roomId, user());
        }
        UserId first = user();
        UserId second = user();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> attemptJoin(start, roomId, first));
            var two = executor.submit(() -> attemptJoin(start, roomId, second));
            start.countDown();

            assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder("joined", "ROOM_FULL");
            assertThat(jdbc.queryForObject(
                    "SELECT member_count FROM app.room WHERE id = ?", Integer.class, roomId.value())).isEqualTo(25);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM app.room_member WHERE room_id = ?", Integer.class, roomId.value()))
                    .isEqualTo(25);
        } finally {
            jdbc.update("DELETE FROM app.room WHERE id = ?", roomId.value());
            jdbc.update("DELETE FROM app.app_user WHERE id IN (?, ?)", first.value(), second.value());
        }
    }

    private String attemptJoin(CountDownLatch start, RoomId roomId, UserId userId) {
        try {
            start.await();
            service.addMember(roomId, userId);
            return "joined";
        } catch (RoomOperationException exception) {
            return exception.code();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private CreateRoomCommand command(String zone) {
        return new CreateRoomCommand("Goa trip", LocalDate.of(2030, 12, 20), LocalTime.of(10, 0),
                zone, "North Goa", "Our first group trip");
    }

    private UserId user() {
        UserId id = new UserId(UUID.randomUUID());
        jdbc.update("""
                INSERT INTO app.app_user (id, status, created_at, updated_at)
                VALUES (?, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id.value());
        return id;
    }

    private void addMember(RoomId roomId, UserId userId, String role) {
        jdbc.update("""
                INSERT INTO app.room_member (room_id, user_id, role, joined_via, joined_at, updated_at)
                VALUES (?, ?, ?, 'invite_link', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, roomId.value(), userId.value(), role);
        jdbc.update("UPDATE app.room SET member_count = member_count + 1 WHERE id = ?", roomId.value());
    }

    private String role(RoomId roomId, UserId userId) {
        return jdbc.queryForObject("SELECT role FROM app.room_member WHERE room_id = ? AND user_id = ?",
                String.class, roomId.value(), userId.value());
    }
}
