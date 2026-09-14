package com.hyped.app.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.application.port.out.RefreshTokenRecordRepository;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.AuthSession;
import com.hyped.app.identity.domain.DeviceId;
import com.hyped.app.identity.domain.DevicePlatform;
import com.hyped.app.identity.domain.DeviceRegistration;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.RefreshTokenId;
import com.hyped.app.identity.domain.RefreshTokenRecord;
import com.hyped.app.identity.domain.RefreshTokenState;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.TokenFamilyId;
import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
class SessionPersistenceIntegrationTest {

    private static final Instant ISSUED = Instant.parse("2026-09-15T10:00:00.123456Z");
    private static final Instant NOW = ISSUED.plusSeconds(60);
    private static final Instant EXPIRES = ISSUED.plusSeconds(3600);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private AuthSessionRepository sessions;

    @Autowired
    private RefreshTokenRecordRepository tokens;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private DeviceRegistrationRepository devices;

    @Autowired
    private RefreshTokenRecordPersistenceMapper mapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void sessionAndActiveTokenSurviveCommittedRoundTrip() {
        UserId owner = user();
        DeviceId deviceId = new DeviceId(UUID.randomUUID());
        devices.save(new DeviceRegistration(deviceId, owner, DevicePlatform.ANDROID,
                new InstallationId(UUID.randomUUID()), "Test phone", null, null, false,
                ISSUED, null, ISSUED, ISSUED));
        AuthSession session = new AuthSession(new SessionId(UUID.randomUUID()), owner, deviceId,
                new TokenFamilyId(UUID.randomUUID()), ISSUED, NOW, EXPIRES, null, null, ISSUED, NOW);
        RefreshTokenRecord token = activeToken(session.id());

        assertThat(sessions.save(session)).isEqualTo(session);
        assertThat(tokens.save(token)).isEqualTo(token);
        assertThat(sessions.findByIdAndUserId(session.id(), owner)).contains(session);
        assertThat(sessions.findByIdAndUserId(session.id(), user())).isEmpty();
        assertThat(tokens.findBySessionId(session.id())).containsExactly(token);
        assertThat(storedState(token.id())).isEqualTo("active");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT success FROM flyway.flyway_schema_history WHERE version = '3'", Boolean.class)).isTrue();
    }

    @Test
    void listsOnlyOwnersUnrevokedUnexpiredSessionsByDescendingLastUse() {
        UserId owner = user();
        AuthSession oldest = sessions.save(session(owner, ISSUED, EXPIRES, null));
        AuthSession newest = sessions.save(session(owner, NOW, EXPIRES, null));
        sessions.save(session(owner, NOW, EXPIRES, NOW));
        sessions.save(session(owner, ISSUED, NOW, null));
        sessions.save(session(owner, ISSUED, NOW.minusSeconds(1), null));
        sessions.save(session(user(), NOW, EXPIRES, null));

        assertThat(sessions.findActiveByUserId(owner, NOW)).containsExactly(newest, oldest);
        assertThat(sessions.findActiveByUserId(new UserId(UUID.randomUUID()), NOW)).isEmpty();
    }

    @Test
    void digestLookupLocksBothSessionAndTokenUntilTransactionEnds() {
        AuthSession session = sessions.save(session(user(), NOW, EXPIRES, null));
        RefreshTokenRecord token = tokens.save(activeToken(session.id()));

        transaction().executeWithoutResult(status -> {
            assertThat(tokens.findByTokenDigestForUpdate(token.tokenDigest())).contains(token);
            assertLockedOnAnotherConnection("app.auth_session", session.id().value());
            assertLockedOnAnotherConnection("app.refresh_token_record", token.id().value());
        });

        assertThat(jdbcTemplate.queryForObject("SELECT id FROM app.auth_session WHERE id = ? FOR UPDATE NOWAIT",
                UUID.class, session.id().value())).isEqualTo(session.id().value());
        assertThat(jdbcTemplate.queryForObject("SELECT id FROM app.refresh_token_record WHERE id = ? FOR UPDATE NOWAIT",
                UUID.class, token.id().value())).isEqualTo(token.id().value());
    }

    @Test
    void lockingLookupRequiresTransactionAndUnknownDigestReturnsEmpty() {
        assertThatThrownBy(() -> tokens.findByTokenDigestForUpdate(digest()))
                .isInstanceOf(IllegalTransactionStateException.class);
        transaction().executeWithoutResult(status ->
                assertThat(tokens.findByTokenDigestForUpdate(digest())).isEmpty());
    }

    @Test
    void rotationConsumesOldTokenBeforeInsertingReplacementAndCommitsBoth() {
        AuthSession session = sessions.save(session(user(), NOW, EXPIRES, null));
        RefreshTokenRecord original = tokens.save(activeToken(session.id()));
        RefreshTokenRecord replacement = new RefreshTokenRecord(new RefreshTokenId(UUID.randomUUID()), session.id(),
                digest(), RefreshTokenState.ACTIVE, NOW, null, EXPIRES, null, NOW);
        RefreshTokenRecord consumed = consumed(original, replacement.id());

        transaction().executeWithoutResult(status -> {
            assertThat(tokens.findByTokenDigestForUpdate(original.tokenDigest())).contains(original);
            assertThat(tokens.save(consumed)).isEqualTo(consumed);
            assertThat(storedState(original.id())).isEqualTo("consumed");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM app.refresh_token_record WHERE session_id = ? AND state = 'active'
                    """, Long.class, session.id().value())).isZero();
            assertThat(tokens.save(replacement)).isEqualTo(replacement);
        });

        assertThat(tokens.findBySessionId(session.id())).containsExactly(consumed, replacement);
        assertThat(storedState(replacement.id())).isEqualTo("active");
        transaction().executeWithoutResult(status ->
                assertThat(tokens.findByTokenDigestForUpdate(original.tokenDigest())).contains(consumed));
    }

    @Test
    void replacementMustExistInSameSessionAtCommitAndRollbackRestoresOriginal() {
        AuthSession session = sessions.save(session(user(), NOW, EXPIRES, null));
        RefreshTokenRecord original = tokens.save(activeToken(session.id()));
        RefreshTokenRecord other = tokens.save(activeToken(sessions.save(session(user(), NOW, EXPIRES, null)).id()));

        assertThatThrownBy(() -> transaction().executeWithoutResult(status ->
                tokens.save(consumed(original, new RefreshTokenId(UUID.randomUUID())))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> transaction().executeWithoutResult(status ->
                tokens.save(consumed(original, other.id()))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(tokens.findBySessionId(session.id())).containsExactly(original);
    }

    @Test
    void insertingReplacementBeforeConsumptionViolatesOneActiveTokenConstraint() {
        AuthSession session = sessions.save(session(user(), NOW, EXPIRES, null));
        RefreshTokenRecord original = tokens.save(activeToken(session.id()));

        assertThatThrownBy(() -> tokens.save(activeToken(session.id())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("refresh_token_record_one_active_per_session_idx");
        assertThat(tokens.findBySessionId(session.id())).containsExactly(original);
    }

    @Test
    void revokedStateUsesLowercaseAndPreservesHistory() {
        AuthSession session = sessions.save(session(user(), NOW, EXPIRES, null));
        RefreshTokenRecord original = tokens.save(activeToken(session.id()));
        RefreshTokenRecord replacement = activeToken(session.id());
        RefreshTokenRecord revoked = new RefreshTokenRecord(original.id(), session.id(), original.tokenDigest(),
                RefreshTokenState.REVOKED, ISSUED, NOW, EXPIRES, replacement.id(), ISSUED);
        transaction().executeWithoutResult(status -> {
            tokens.save(revoked);
            tokens.save(replacement);
        });

        assertThat(storedState(original.id())).isEqualTo("revoked");
        assertThat(tokens.findBySessionId(session.id())).containsExactlyInAnyOrder(revoked, replacement);
    }

    @Test
    void digestArraysAreCopiedAcrossAllBoundaries() {
        AuthSession session = sessions.save(session(user(), NOW, EXPIRES, null));
        byte[] input = digest();
        byte[] expected = input.clone();
        RefreshTokenRecord token = new RefreshTokenRecord(new RefreshTokenId(UUID.randomUUID()), session.id(), input,
                RefreshTokenState.ACTIVE, ISSUED, null, EXPIRES, null, ISSUED);
        input[0]++;
        token.tokenDigest()[0]++;
        RefreshTokenRecordEntity entity = mapper.toEntity(token);
        byte[] entityInput = expected.clone();
        entity.setTokenDigest(entityInput);
        entityInput[0]++;
        entity.getTokenDigest()[0]++;
        RefreshTokenRecord mapped = mapper.toDomain(entity);
        entity.setTokenDigest(digest());
        assertThat(mapped).isEqualTo(token);
        RefreshTokenRecord saved = tokens.save(mapped);
        saved.tokenDigest()[0]++;

        byte[] lookup = expected.clone();
        transaction().executeWithoutResult(status -> {
            RefreshTokenRecord loaded = tokens.findByTokenDigestForUpdate(lookup).orElseThrow();
            lookup[0]++;
            loaded.tokenDigest()[0]++;
            assertThat(loaded.tokenDigest()).containsExactly(expected);
        });
        assertThat(tokens.findBySessionId(session.id())).containsExactly(token);
    }

    private void assertLockedOnAnotherConnection(String table, UUID id) {
        // Table names are fixed test constants, never request input.
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM " + table + " WHERE id = ? FOR UPDATE NOWAIT")) {
            statement.setObject(1, id);
            assertThatThrownBy(statement::executeQuery).isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("55P03"));
        } catch (SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private String storedState(RefreshTokenId id) {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM app.refresh_token_record WHERE id = ?", String.class, id.value());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private UserId user() {
        UserId id = new UserId(UUID.randomUUID());
        users.save(new UserAccount(id, AccountStatus.ACTIVE, null, 0, null, null, null, null, ISSUED, ISSUED));
        return id;
    }

    private static AuthSession session(UserId owner, Instant lastUsed, Instant expires, Instant revoked) {
        return new AuthSession(new SessionId(UUID.randomUUID()), owner, null, new TokenFamilyId(UUID.randomUUID()),
                ISSUED, lastUsed, expires, revoked, revoked == null ? null : "logout", ISSUED, NOW);
    }

    private static RefreshTokenRecord activeToken(SessionId sessionId) {
        return new RefreshTokenRecord(new RefreshTokenId(UUID.randomUUID()), sessionId, digest(),
                RefreshTokenState.ACTIVE, ISSUED, null, EXPIRES, null, ISSUED);
    }

    private static RefreshTokenRecord consumed(RefreshTokenRecord token, RefreshTokenId replacementId) {
        return new RefreshTokenRecord(token.id(), token.sessionId(), token.tokenDigest(),
                RefreshTokenState.CONSUMED, token.issuedAt(), NOW, token.expiresAt(), replacementId, token.createdAt());
    }

    private static byte[] digest() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        return ByteBuffer.allocate(32).putLong(first.getMostSignificantBits()).putLong(first.getLeastSignificantBits())
                .putLong(second.getMostSignificantBits()).putLong(second.getLeastSignificantBits()).array();
    }
}
