package com.hyped.app.identity.application.service;

import com.hyped.app.identity.application.model.ActiveSessionSummary;
import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.domain.AuthSession;
import com.hyped.app.identity.domain.DeviceRegistration;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserId;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ListActiveSessionsService {
    private final AuthSessionRepository sessions;
    private final DeviceRegistrationRepository devices;
    private final Clock clock;

    public ListActiveSessionsService(
            AuthSessionRepository sessions,
            DeviceRegistrationRepository devices,
            Clock clock) {
        this.sessions = sessions;
        this.devices = devices;
        this.clock = clock;
    }

    public List<ActiveSessionSummary> list(UserId userId, SessionId currentSessionId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(currentSessionId, "currentSessionId");
        return sessions.findActiveByUserId(userId, clock.instant()).stream()
                .map(session -> summary(session, userId, currentSessionId))
                .toList();
    }

    private ActiveSessionSummary summary(
            AuthSession session, UserId userId, SessionId currentSessionId) {
        if (session.deviceId() == null) {
            throw new IllegalStateException("An active session has no device registration");
        }
        DeviceRegistration device = devices.findByIdAndUserId(session.deviceId(), userId)
                .orElseThrow(() -> new IllegalStateException("An active session device is unavailable"));
        return new ActiveSessionSummary(session.id(), device.deviceName(), device.platform(),
                session.createdAt(), session.lastUsedAt(), session.id().equals(currentSessionId));
    }
}
