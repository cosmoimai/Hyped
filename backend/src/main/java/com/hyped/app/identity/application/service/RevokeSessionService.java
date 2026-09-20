package com.hyped.app.identity.application.service;

import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.application.port.out.RefreshTokenRecordRepository;
import com.hyped.app.identity.domain.AuthSession;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokeSessionService {
    private final AuthSessionRepository sessions;
    private final RefreshTokenRecordRepository tokens;
    private final DeviceRegistrationRepository devices;
    private final Clock clock;

    public RevokeSessionService(
            AuthSessionRepository sessions,
            RefreshTokenRecordRepository tokens,
            DeviceRegistrationRepository devices,
            Clock clock) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.devices = devices;
        this.clock = clock;
    }

    /** Unknown and differently owned sessions both return false. Repeated revocation is safe. */
    @Transactional
    public boolean revoke(UserId userId, SessionId sessionId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(sessionId, "sessionId");
        AuthSession session = sessions.findByIdAndUserIdForUpdate(sessionId, userId).orElse(null);
        if (session == null) {
            return false;
        }
        Instant now = clock.instant();
        SessionRevocation.revoke(session, now, "device_removed", sessions, tokens);
        if (session.deviceId() != null) {
            devices.invalidate(session.deviceId(), userId, now);
        }
        return true;
    }
}
