package com.hyped.app.notification.infrastructure;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.hyped.app.notification.application.port.out.PushSender;
import java.util.Map;

public class FirebasePushSender implements PushSender {
    private final FirebaseMessaging messaging;

    public FirebasePushSender(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public Outcome send(String token, Map<String, String> data) {
        try {
            messaging.send(Message.builder().setToken(token).putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH)
                            .setTtl(300_000).build()).build());
            return Outcome.SENT;
        } catch (FirebaseMessagingException failure) {
            var code = failure.getMessagingErrorCode();
            if (code == null) {
                return Outcome.UNKNOWN;
            }
            return switch (code) {
                case UNREGISTERED, SENDER_ID_MISMATCH -> Outcome.INVALID_TOKEN;
                case UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED -> Outcome.RETRY;
                default -> Outcome.FAILED;
            };
        } catch (RuntimeException failure) {
            return Outcome.UNKNOWN;
        }
    }
}
