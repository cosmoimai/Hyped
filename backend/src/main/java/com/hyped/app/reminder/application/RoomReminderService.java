package com.hyped.app.reminder.application;

import com.hyped.app.reminder.application.port.out.RoomReminderRepository;
import com.hyped.app.reminder.domain.RoomReminder;
import com.hyped.app.room.domain.RoomId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomReminderService {
    public static final int MAX_CLAIM_LIMIT = 100;
    public static final Duration CLAIM_LEASE = Duration.ofMinutes(10);
    private final RoomReminderRepository reminders;
    private final Clock clock;

    public RoomReminderService(RoomReminderRepository reminders, Clock clock) {
        this.reminders = reminders;
        this.clock = clock;
    }

    @Transactional
    public void replaceForEvent(RoomId roomId, Instant eventAt) {
        reminders.replacePending(roomId, eventAt, clock.instant());
    }

    @Transactional
    public void cancelOpen(RoomId roomId) {
        reminders.cancelOpen(roomId, clock.instant());
    }

    @Transactional
    public List<RoomReminder> claimDue(int limit) {
        if (limit < 1 || limit > MAX_CLAIM_LIMIT) {
            throw new IllegalArgumentException("Claim limit must be between 1 and " + MAX_CLAIM_LIMIT);
        }
        return reminders.claimDue(clock.instant(), limit, CLAIM_LEASE);
    }

    @Transactional
    public boolean complete(UUID reminderId, UUID claimToken) {
        return reminders.complete(reminderId, claimToken, clock.instant());
    }
}
