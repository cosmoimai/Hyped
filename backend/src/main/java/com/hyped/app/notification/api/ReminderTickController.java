package com.hyped.app.notification.api;

import com.hyped.app.notification.application.ReminderDeliveryService;
import com.hyped.app.room.application.RoomService;
import com.hyped.app.account.application.AccountService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnExpression("${hyped.push.enabled:false} || ${hyped.account-deletion.enabled:false}")
public class ReminderTickController {
    private final ObjectProvider<ReminderDeliveryService> service;
    private final RoomService rooms;
    private final ObjectProvider<AccountService> accounts;

    public ReminderTickController(ObjectProvider<ReminderDeliveryService> service, RoomService rooms,
            ObjectProvider<AccountService> accounts) {
        this.service = service;
        this.rooms = rooms;
        this.accounts = accounts;
    }

    private ObjectProvider<com.hyped.app.media.application.MediaService> media;
    private com.hyped.app.moderation.application.SafetyService safety;
    @org.springframework.beans.factory.annotation.Autowired
    public void safety(com.hyped.app.moderation.application.SafetyService value) { safety = value; }
    @org.springframework.beans.factory.annotation.Autowired
    public void media(ObjectProvider<com.hyped.app.media.application.MediaService> provider) { media = provider; }
    @PostMapping("/internal/jobs/tick")
    public ReminderDeliveryService.Result tick() {
        try {
            var reminders = service.getIfAvailable();
            var result = reminders == null ? new ReminderDeliveryService.Result(0, 0, 0) : reminders.tick();
            rooms.purgeEligible();
            return result;
        } finally {
            // Irreversible account cleanup must also run when an unrelated reminder/room task fails.
            try {
                accounts.ifAvailable(AccountService::tick);
            } finally {
                try {
                    if (media != null) { media.ifAvailable(com.hyped.app.media.application.MediaService::cleanup); }
                } finally {
                    if (safety != null) { safety.cleanup(); }
                }
            }
        }
    }
}
