package com.hyped.app.reminder.application.port.out;

import com.hyped.app.reminder.domain.RoomReminder;
import com.hyped.app.room.domain.RoomId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RoomReminderRepository {
    void replacePending(RoomId roomId, Instant eventAt, Instant now);

    int cancelOpen(RoomId roomId, Instant now);

    List<RoomReminder> claimDue(Instant now, int limit, Duration lease);

    boolean complete(UUID reminderId, UUID claimToken, Instant now);
}
