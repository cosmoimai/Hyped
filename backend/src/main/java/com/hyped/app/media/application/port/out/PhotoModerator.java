package com.hyped.app.media.application.port.out;

public interface PhotoModerator {
    enum Decision {
        APPROVED,
        UNCERTAIN,
        REJECTED
    }

    Decision evaluate(byte[] sanitized);
}
