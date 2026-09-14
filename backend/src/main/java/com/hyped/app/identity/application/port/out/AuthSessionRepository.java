package com.hyped.app.identity.application.port.out;

import com.hyped.app.identity.domain.AuthSession;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository {

    Optional<AuthSession> findByIdAndUserId(SessionId sessionId, UserId userId);

    /** Returns unrevoked, unexpired sessions ordered by lastUsedAt descending. */
    List<AuthSession> findActiveByUserId(UserId userId, Instant now);

    AuthSession save(AuthSession session);
}
