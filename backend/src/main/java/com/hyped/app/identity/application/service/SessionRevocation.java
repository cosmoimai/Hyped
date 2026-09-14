package com.hyped.app.identity.application.service;

import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.RefreshTokenRecordRepository;
import com.hyped.app.identity.domain.AuthSession;
import java.time.Instant;

final class SessionRevocation {

    private SessionRevocation() {}

    // The caller holds the session lock and owns the transaction.
    static void revoke(AuthSession session, Instant now, String reason,
            AuthSessionRepository sessions, RefreshTokenRecordRepository tokens) {
        if (session.revokedAt() == null) {
            sessions.save(new AuthSession(session.id(), session.userId(), session.deviceId(), session.tokenFamilyId(),
                    session.issuedAt(), session.lastUsedAt(), session.expiresAt(), now, reason,
                    session.createdAt(), now));
        }
        tokens.revokeBySessionId(session.id());
    }
}
