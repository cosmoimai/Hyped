package com.hyped.app.common.security;

import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserId;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** A valid signature must not resurrect a revoked session or invalidated installation. */
@Component
public class SessionStateVerifier {
    private final AuthSessionRepository sessions;
    private final DeviceRegistrationRepository devices;
    private final Clock clock;

    public SessionStateVerifier(AuthSessionRepository sessions, DeviceRegistrationRepository devices, Clock clock) {
        this.sessions = sessions;
        this.devices = devices;
        this.clock = clock;
    }

    public boolean active(Jwt jwt) {
        var user = new UserId(UUID.fromString(jwt.getSubject()));
        var session = sessions.findByIdAndUserId(
                new SessionId(UUID.fromString(jwt.getClaimAsString("sid"))), user).orElse(null);
        if (session == null || session.revokedAt() != null || !session.expiresAt().isAfter(clock.instant())
                || session.deviceId() == null) {
            return false;
        }
        var device = devices.findByIdAndUserId(session.deviceId(), user).orElse(null);
        return device != null && device.invalidatedAt() == null
                && device.installationId().toString().equals(jwt.getClaimAsString("did"));
    }
}
