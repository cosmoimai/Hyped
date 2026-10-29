package com.hyped.app.notification.application;

/** The authenticated registration binding changed during external encryption. */
public final class PushRegistrationException extends RuntimeException {
    public PushRegistrationException() {
        super("Sign in again before registering notifications.");
    }
}
