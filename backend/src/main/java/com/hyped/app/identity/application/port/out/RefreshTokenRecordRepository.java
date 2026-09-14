package com.hyped.app.identity.application.port.out;

import com.hyped.app.identity.domain.RefreshTokenRecord;
import com.hyped.app.identity.domain.SessionId;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRecordRepository {

    /**
     * Flushes the saved record. Rotation must save the old token as consumed before
     * inserting its replacement, within one transaction that commits both writes.
     */
    RefreshTokenRecord save(RefreshTokenRecord token);

    /** Requires an active transaction and explicitly flushes pending token writes. */
    void flush();

    /** Requires an active transaction with the session locked; preserves consumption history. */
    void revokeBySessionId(SessionId sessionId);

    /** Requires an active transaction; locks the token and its session until transaction completion. */
    Optional<RefreshTokenRecord> findByTokenDigestForUpdate(byte[] tokenDigest);

    List<RefreshTokenRecord> findBySessionId(SessionId sessionId);
}
