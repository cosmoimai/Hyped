package com.hyped.app.identity.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataRefreshTokenRecordRepository extends JpaRepository<RefreshTokenRecordEntity, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE app.refresh_token_record SET state = 'revoked' WHERE session_id = :sessionId",
            nativeQuery = true)
    int revokeBySessionId(@Param("sessionId") UUID sessionId);

    @Query(value = """
            SELECT r.*
            FROM app.refresh_token_record r
            JOIN app.auth_session s ON s.id = r.session_id
            WHERE r.token_digest = :tokenDigest
            FOR UPDATE OF s, r
            """, nativeQuery = true)
    Optional<RefreshTokenRecordEntity> findByTokenDigestForUpdate(@Param("tokenDigest") byte[] tokenDigest);

    List<RefreshTokenRecordEntity> findBySessionIdOrderByIssuedAtAscIdAsc(UUID sessionId);
}
