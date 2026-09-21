package com.hyped.app.identity.application.port.out;

import com.hyped.app.identity.domain.AuthSession;
import com.hyped.app.identity.domain.DeviceId;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository {

    Optional<AuthSession> findById(SessionId sessionId);

    /** Requires an active transaction; locks the owner-scoped session until transaction completion. */
    Optional<AuthSession> findByIdAndUserIdForUpdate(SessionId sessionId, UserId userId);

    Optional<AuthSession> findByIdAndUserId(SessionId sessionId, UserId userId);

    /** Returns unrevoked, unexpired sessions ordered by lastUsedAt descending. */
    List<AuthSession> findActiveByUserId(UserId userId, Instant now);

    /** Requires an active transaction; locks every session for the owner-scoped device. */
    List<AuthSession> findByDeviceIdAndUserIdForUpdate(DeviceId deviceId, UserId userId);

    AuthSession save(AuthSession session);
}
