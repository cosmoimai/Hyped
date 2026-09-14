package com.hyped.app.identity.application.service;

import com.hyped.app.identity.application.model.GeneratedRefreshToken;
import com.hyped.app.identity.application.model.IssuedAccessToken;
import com.hyped.app.identity.application.model.RefreshSessionResult;
import com.hyped.app.identity.application.model.SessionTokenPair;
import com.hyped.app.identity.application.port.out.AccessTokenIssuer;
import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.application.port.out.IdGenerator;
import com.hyped.app.identity.application.port.out.RefreshTokenDigester;
import com.hyped.app.identity.application.port.out.RefreshTokenGenerator;
import com.hyped.app.identity.application.port.out.RefreshTokenRecordRepository;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.domain.AuthSession;
import com.hyped.app.identity.domain.DeviceRegistration;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.RefreshTokenId;
import com.hyped.app.identity.domain.RefreshTokenRecord;
import com.hyped.app.identity.domain.RefreshTokenState;
import com.hyped.app.identity.domain.UserAccount;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

public class RefreshSessionService {

    private final RefreshTokenDigester digester;
    private final RefreshTokenRecordRepository tokens;
    private final AuthSessionRepository sessions;
    private final UserAccountRepository users;
    private final DeviceRegistrationRepository devices;
    private final RefreshTokenGenerator generator;
    private final AccessTokenIssuer issuer;
    private final IdGenerator ids;
    private final Clock clock;

    public RefreshSessionService(RefreshTokenDigester digester, RefreshTokenRecordRepository tokens,
            AuthSessionRepository sessions, UserAccountRepository users, DeviceRegistrationRepository devices,
            RefreshTokenGenerator generator, AccessTokenIssuer issuer, IdGenerator ids, Clock clock) {
        this.digester = digester;
        this.tokens = tokens;
        this.sessions = sessions;
        this.users = users;
        this.devices = devices;
        this.generator = generator;
        this.issuer = issuer;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public RefreshSessionResult refresh(String rawRefreshToken, InstallationId installationId) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank() || installationId == null) {
            return denied("SESSION_EXPIRED");
        }
        RefreshTokenRecord token = tokens.findByTokenDigestForUpdate(digester.digest(rawRefreshToken)).orElse(null);
        if (token == null) {
            return denied("SESSION_EXPIRED");
        }
        AuthSession session = sessions.findById(token.sessionId()).orElseThrow();
        Instant now = clock.instant();
        if (token.state() == RefreshTokenState.CONSUMED) {
            SessionRevocation.revoke(session, now, "refresh_token_reuse", sessions, tokens);
            // Returning normally allows revocation to commit before an eventual API error is sent.
            return denied("REFRESH_TOKEN_REUSE_DETECTED");
        }
        if (token.state() != RefreshTokenState.ACTIVE || !token.expiresAt().isAfter(now)
                || session.revokedAt() != null || !session.expiresAt().isAfter(now)) {
            return denied("SESSION_EXPIRED");
        }
        UserAccount account = users.findById(session.userId()).orElse(null);
        if (account == null || !account.canStartSession(now)) {
            return denied("ACCOUNT_UNAVAILABLE");
        }
        DeviceRegistration device = session.deviceId() == null ? null
                : devices.findByIdAndUserId(session.deviceId(), session.userId()).orElse(null);
        if (device == null || !device.isActive() || !device.installationId().equals(installationId)) {
            return denied("SESSION_DEVICE_MISMATCH");
        }
        GeneratedRefreshToken generated = generator.generate();
        Instant expiresAt = generated.expiresAt().isBefore(session.expiresAt())
                ? generated.expiresAt() : session.expiresAt();
        if (!expiresAt.isAfter(generated.issuedAt())) {
            return denied("SESSION_EXPIRED");
        }
        RefreshTokenId replacementId = new RefreshTokenId(ids.generate());
        RefreshTokenRecord consumed = new RefreshTokenRecord(token.id(), token.sessionId(), token.tokenDigest(),
                RefreshTokenState.CONSUMED, token.issuedAt(), now, token.expiresAt(), replacementId, token.createdAt());
        tokens.save(consumed);
        tokens.flush();
        tokens.save(new RefreshTokenRecord(replacementId, session.id(), digester.digest(generated.tokenValue()),
                RefreshTokenState.ACTIVE, generated.issuedAt(), null, expiresAt, null, generated.issuedAt()));
        sessions.save(new AuthSession(session.id(), session.userId(), session.deviceId(), session.tokenFamilyId(),
                session.issuedAt(), now, session.expiresAt(), null, null, session.createdAt(), now));
        IssuedAccessToken accessToken = issuer.issue(session.userId(), session.id(), device.installationId());
        GeneratedRefreshToken replacement = new GeneratedRefreshToken(generated.tokenValue(),
                generated.issuedAt(), expiresAt);
        return new RefreshSessionResult.Success(new SessionTokenPair(accessToken, replacement));
    }

    private static RefreshSessionResult denied(String code) {
        return new RefreshSessionResult.ReauthenticationRequired(code);
    }
}
