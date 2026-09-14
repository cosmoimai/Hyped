package com.hyped.app.identity.application.service;

import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.RefreshTokenRecordRepository;
import com.hyped.app.identity.domain.AuthSession;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserId;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutSessionService {

    private final AuthSessionRepository sessions;
    private final RefreshTokenRecordRepository tokens;
    private final Clock clock;

    public LogoutSessionService(AuthSessionRepository sessions, RefreshTokenRecordRepository tokens, Clock clock) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.clock = clock;
    }

    /** Returns false for an absent or differently owned session. Repeated logout is idempotent. */
    @Transactional
    public boolean logout(UserId userId, SessionId sessionId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(sessionId, "sessionId");
        AuthSession session = sessions.findByIdAndUserIdForUpdate(sessionId, userId).orElse(null);
        if (session == null) {
            return false;
        }
        SessionRevocation.revoke(session, clock.instant(), "logout", sessions, tokens);
        return true;
    }
}
