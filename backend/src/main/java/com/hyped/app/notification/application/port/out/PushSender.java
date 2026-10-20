package com.hyped.app.notification.application.port.out;

import java.util.Map;

public interface PushSender {
    Outcome send(String token, Map<String, String> data);

    enum Outcome {
        SENT, INVALID_TOKEN, RETRY, FAILED, UNKNOWN, CANCELED
    }
}
