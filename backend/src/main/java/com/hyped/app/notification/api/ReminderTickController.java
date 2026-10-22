package com.hyped.app.notification.api;

import com.hyped.app.notification.application.ReminderDeliveryService;
import com.hyped.app.room.application.RoomService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "hyped.push", name = "enabled", havingValue = "true")
public class ReminderTickController {
    private final ReminderDeliveryService service;
    private final RoomService rooms;

    public ReminderTickController(ReminderDeliveryService service, RoomService rooms) {
        this.service = service;
        this.rooms = rooms;
    }

    @PostMapping("/internal/jobs/tick")
    public ReminderDeliveryService.Result tick() {
        var result = service.tick();
        rooms.purgeEligible();
        return result;
    }
}
