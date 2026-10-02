package com.hyped.app.reminder.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.reminder.application.RoomReminderService;
import com.hyped.app.reminder.domain.RoomReminder;
import com.hyped.app.room.application.RoomService;
import com.hyped.app.room.application.RoomService.CreateRoomCommand;
import com.hyped.app.room.application.RoomService.UpdateRoomCommand;
import com.hyped.app.room.domain.RoomId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
@Import(RoomReminderIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoomReminderIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-03T08:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomReminderService reminderService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM app.room");
        jdbc.update("DELETE FROM app.app_user");
        clock.set(NOW);
    }

    @Test
    void creatingRoomSchedulesThreeRoomReminders() {
        RoomId roomId = roomService.create(user(), command(LocalDate.of(2030, 12, 20), LocalTime.of(10, 0)))
                .room().id();

        assertThat(reminders(roomId))
                .extracting(row -> row.kind + " " + row.status + " " + row.scheduledAt)
                .containsExactlyInAnyOrder(
                        "day_before pending 2030-12-19T10:00:00Z",
                        "hour_before pending 2030-12-20T09:00:00Z",
                        "event_time pending 2030-12-20T10:00:00Z");
    }

    @Test
    void updatingEventTimeCancelsOldOpenRemindersAndCreatesNewOnes() {
        UserId owner = user();
        RoomId roomId = roomService.create(owner, command(LocalDate.of(2030, 12, 20), LocalTime.of(10, 0)))
                .room().id();

        roomService.update(owner, roomId, 1, update(LocalDate.of(2030, 12, 21), LocalTime.of(10, 0)));
        roomService.update(owner, roomId, 2, update(LocalDate.of(2030, 12, 20), LocalTime.of(10, 0)));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM app.room_reminder
                WHERE room_id = ? AND status = 'canceled'
                """, Integer.class, roomId.value())).isEqualTo(6);
        assertThat(reminders(roomId))
                .filteredOn(row -> row.status.equals("pending"))
                .extracting(row -> row.kind)
                .containsExactlyInAnyOrder("day_before", "hour_before", "event_time");
    }

    @Test
    void nearFutureEventCreatesDueLeadTimeRemindersWithoutClaimingEventTimeEarly() {
        clock.set(Instant.parse("2030-12-20T09:29:00Z"));
        roomService.create(user(), command(LocalDate.of(2030, 12, 20), LocalTime.of(10, 0)));

        List<RoomReminder> claimed = reminderService.claimDue(10);

        assertThat(claimed).extracting(RoomReminder::kind)
                .containsExactlyInAnyOrder(RoomReminder.Kind.DAY_BEFORE, RoomReminder.Kind.HOUR_BEFORE);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM app.room_reminder
                WHERE kind = 'event_time' AND status = 'pending'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void archivedRoomCancelsOpenRemindersAndPreventsClaiming() {
        clock.set(Instant.parse("2030-12-20T09:29:00Z"));
        UserId owner = user();
        RoomId roomId = roomService.create(owner, command(LocalDate.of(2030, 12, 20), LocalTime.of(10, 0)))
                .room().id();

        roomService.archive(owner, roomId);

        assertThat(reminderService.claimDue(10)).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM app.room_reminder
                WHERE room_id = ? AND status = 'canceled'
                """, Integer.class, roomId.value())).isEqualTo(3);
    }

    @Test
    void completingClaimedReminderIsIdempotentForTheClaimToken() {
        clock.set(Instant.parse("2030-12-20T09:29:00Z"));
        roomService.create(user(), command(LocalDate.of(2030, 12, 20), LocalTime.of(10, 0)));
        RoomReminder reminder = reminderService.claimDue(1).getFirst();

        assertThat(reminderService.complete(reminder.id(), reminder.claimToken())).isTrue();
        assertThat(reminderService.complete(reminder.id(), reminder.claimToken())).isTrue();
        assertThat(reminderService.complete(reminder.id(), UUID.randomUUID())).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM app.room_reminder
                WHERE id = ? AND status = 'completed'
                """, Integer.class, reminder.id())).isEqualTo(1);
    }

    @Test
    void concurrentClaimsDoNotReturnTheSameReminder() throws Exception {
        clock.set(Instant.parse("2030-12-20T09:29:00Z"));
        for (int index = 0; index < 4; index++) {
            roomService.create(user(), command(LocalDate.of(2030, 12, 20), LocalTime.of(10, 0)));
        }
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> claimAfter(start));
            var second = executor.submit(() -> claimAfter(start));
            start.countDown();

            List<UUID> claimedIds = new ArrayList<>();
            first.get().forEach(reminder -> claimedIds.add(reminder.id()));
            second.get().forEach(reminder -> claimedIds.add(reminder.id()));

            assertThat(claimedIds).hasSize(8).doesNotHaveDuplicates();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM app.room_reminder
                    WHERE status = 'claimed'
                    """, Integer.class)).isEqualTo(8);
        }
    }

    @Test
    void invalidClaimLimitIsRejectedBeforePolling() {
        assertThatThrownBy(() -> reminderService.claimDue(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reminderService.claimDue(101)).isInstanceOf(IllegalArgumentException.class);
    }

    private List<RoomReminder> claimAfter(CountDownLatch start) throws InterruptedException {
        start.await();
        return reminderService.claimDue(4);
    }

    private CreateRoomCommand command(LocalDate date, LocalTime time) {
        return new CreateRoomCommand("Goa trip", date, time, "UTC", "North Goa", "Our first group trip");
    }

    private UpdateRoomCommand update(LocalDate date, LocalTime time) {
        return new UpdateRoomCommand(null, date, time, "UTC", null, false, null, false);
    }

    private UserId user() {
        UserId id = new UserId(UUID.randomUUID());
        jdbc.update("""
                INSERT INTO app.app_user (id, status, created_at, updated_at)
                VALUES (?, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id.value());
        return id;
    }

    private List<ReminderRow> reminders(RoomId roomId) {
        return jdbc.query("""
                SELECT kind, status, scheduled_at
                FROM app.room_reminder
                WHERE room_id = ?
                ORDER BY scheduled_at, kind
                """, (rs, row) -> new ReminderRow(rs.getString("kind"), rs.getString("status"),
                rs.getObject("scheduled_at", java.time.OffsetDateTime.class).toInstant()), roomId.value());
    }

    record ReminderRow(String kind, String status, Instant scheduledAt) {
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock reminderTestClock() {
            return new MutableClock(NOW);
        }
    }

    static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

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
            return instant;
        }
    }
}
