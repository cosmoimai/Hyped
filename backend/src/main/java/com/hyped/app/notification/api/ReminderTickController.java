package com.hyped.app.notification.api;

import com.hyped.app.notification.application.ReminderDeliveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "hyped.push", name = "enabled", havingValue = "true")
public class ReminderTickController {
    private final ReminderDeliveryService service;

    public ReminderTickController(ReminderDeliveryService service) {
        this.service = service;
    }

    @PostMapping("/internal/jobs/tick")
    public ReminderDeliveryService.Result tick() {
        return service.tick();
    }
}
