package com.hyped.app.notification.application;

import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.notification.application.port.out.PushSender;
import com.hyped.app.notification.application.port.out.ReminderDeliveryRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "hyped.push", name = "enabled", havingValue = "true")
public class ReminderDeliveryService {
    private final ReminderDeliveryRepository deliveries;
    private final PersonalDataCipher cipher;
    private final PushSender sender;
    private final Clock clock;

    public ReminderDeliveryService(ReminderDeliveryRepository deliveries,
            PersonalDataCipher cipher, PushSender sender, Clock clock) {
        this.deliveries = deliveries;
        this.cipher = cipher;
        this.sender = sender;
        this.clock = clock;
    }

    public Result tick() {
        int prepared = deliveries.prepare(100, clock.instant());
        int sent = 0;
        int failed = 0;
        for (var delivery : deliveries.claim(100, clock.instant())) {
            if (!deliveries.stillEligible(delivery, clock.instant())) {
                deliveries.finish(delivery, PushSender.Outcome.CANCELED, clock.instant());
                continue;
            }
            PushSender.Outcome outcome;
            byte[] token = null;
            try {
                token = cipher.decrypt(new UserId(delivery.userId()), delivery.keyReference(),
                        PersonalDataField.DEVICE_FCM_TOKEN, delivery.ciphertext());
                outcome = sender.send(new String(token, StandardCharsets.UTF_8), Map.of(
                        "type", "EVENT_REMINDER", "roomId", delivery.roomId().toString(),
                        "roomRevision", Long.toString(delivery.revision()), "notificationId", delivery.id().toString(),
                        "sessionId", delivery.sessionId().toString(), "reminderKind", delivery.kind()));
            } catch (RuntimeException failure) {
                // Never propagate/log provider or decrypt exceptions: they can contain a device token.
                outcome = PushSender.Outcome.UNKNOWN;
            } finally {
                if (token != null) {
                    Arrays.fill(token, (byte) 0);
                }
            }
            deliveries.finish(delivery, outcome, clock.instant());
            if (outcome == PushSender.Outcome.SENT) {
                sent++;
            } else {
                failed++;
            }
        }
        return new Result(prepared, sent, failed);
    }

    public record Result(int prepared, int sent, int failed) {
    }
}
