package com.hyped.app.identity.application.service;

import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.application.port.out.RefreshTokenRecordRepository;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.domain.DeviceId;
import com.hyped.app.identity.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoverDeviceTransactionService {
    private final UserAccountRepository users;
    private final AuthSessionRepository sessions;
    private final RefreshTokenRecordRepository tokens;
    private final DeviceRegistrationRepository devices;
    private final Clock clock;

    public RecoverDeviceTransactionService(
            UserAccountRepository users,
            AuthSessionRepository sessions,
            RefreshTokenRecordRepository tokens,
            DeviceRegistrationRepository devices,
            Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.tokens = tokens;
        this.devices = devices;
        this.clock = clock;
    }

    @Transactional
    public boolean revokeDevice(UserId userId, DeviceId deviceId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(deviceId, "deviceId");
        if (users.findByIdForUpdate(userId).isEmpty()) {
            return false;
        }
        Instant now = clock.instant();
        sessions.findByDeviceIdAndUserIdForUpdate(deviceId, userId)
                .forEach(session -> SessionRevocation.revoke(
                        session, now, "device_recovery", sessions, tokens));
        return devices.invalidate(deviceId, userId, now);
    }
}
