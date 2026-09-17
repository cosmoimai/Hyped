package com.hyped.app.identity.application.service;

import com.hyped.app.identity.application.model.CreateSessionCommand;
import com.hyped.app.identity.application.model.CreateSessionResult;
import com.hyped.app.identity.application.model.DeviceSummary;
import com.hyped.app.identity.application.model.GeneratedRefreshToken;
import com.hyped.app.identity.application.model.IssuedAccessToken;
import com.hyped.app.identity.application.model.SessionTokenPair;
import com.hyped.app.identity.application.port.out.AccessTokenIssuer;
import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.application.port.out.IdGenerator;
import com.hyped.app.identity.application.port.out.RefreshTokenDigester;
import com.hyped.app.identity.application.port.out.RefreshTokenGenerator;
import com.hyped.app.identity.application.port.out.RefreshTokenRecordRepository;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.AuthSession;
import com.hyped.app.identity.domain.DeviceId;
import com.hyped.app.identity.domain.DeviceRegistration;
import com.hyped.app.identity.domain.RefreshTokenId;
import com.hyped.app.identity.domain.RefreshTokenRecord;
import com.hyped.app.identity.domain.RefreshTokenState;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.TokenFamilyId;
import com.hyped.app.identity.domain.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class CreateSessionService {

    static final int MAX_ACTIVE_DEVICES = 5;

    private final UserAccountRepository users;
    private final DeviceRegistrationRepository devices;
    private final AuthSessionRepository sessions;
    private final RefreshTokenRecordRepository tokens;
    private final RefreshTokenGenerator tokenGenerator;
    private final RefreshTokenDigester tokenDigester;
    private final AccessTokenIssuer accessTokenIssuer;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateSessionService(UserAccountRepository users, DeviceRegistrationRepository devices,
            AuthSessionRepository sessions, RefreshTokenRecordRepository tokens,
            RefreshTokenGenerator tokenGenerator, RefreshTokenDigester tokenDigester,
            AccessTokenIssuer accessTokenIssuer, IdGenerator ids, Clock clock) {
        this.users = users;
        this.devices = devices;
        this.sessions = sessions;
        this.tokens = tokens;
        this.tokenGenerator = tokenGenerator;
        this.tokenDigester = tokenDigester;
        this.accessTokenIssuer = accessTokenIssuer;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public CreateSessionResult create(CreateSessionCommand command) {
        Objects.requireNonNull(command, "command");
        Instant now = clock.instant();
        UserAccount account = users.findByIdForUpdate(command.userId()).orElse(null);
        if (account == null) {
            return new CreateSessionResult.AccountBlocked(AccountStatus.DELETED);
        }
        CreateSessionResult unavailable = unavailableAccountResult(account, now);
        if (unavailable != null) {
            return unavailable;
        }

        DeviceRegistration existing = devices
                .findByUserAndInstallation(command.userId(), command.installationId()).orElse(null);
        if ((existing == null || !existing.isActive())
                && devices.countActiveByUserId(command.userId()) >= MAX_ACTIVE_DEVICES) {
            return new CreateSessionResult.DeviceLimitReached(activeDeviceSummaries(command));
        }

        DeviceRegistration device = devices.save(upsertDevice(command, existing, now));
        revokeEarlierSessions(command, device, now);
        UserAccount authenticated = account.recordSuccessfulAuthentication(now);
        users.save(authenticated);

        GeneratedRefreshToken refreshToken = tokenGenerator.generate();
        SessionId sessionId = new SessionId(ids.generate());
        AuthSession session = sessions.save(new AuthSession(sessionId, command.userId(), device.id(),
                new TokenFamilyId(ids.generate()), refreshToken.issuedAt(), refreshToken.issuedAt(),
                refreshToken.expiresAt(), null, null, refreshToken.issuedAt(), refreshToken.issuedAt()));
        tokens.save(new RefreshTokenRecord(new RefreshTokenId(ids.generate()), session.id(),
                tokenDigester.digest(refreshToken.tokenValue()), RefreshTokenState.ACTIVE,
                refreshToken.issuedAt(), null, refreshToken.expiresAt(), null, refreshToken.issuedAt()));
        IssuedAccessToken accessToken = accessTokenIssuer.issue(
                command.userId(), session.id(), command.installationId());
        return new CreateSessionResult.Success(new SessionTokenPair(accessToken, refreshToken));
    }

    private CreateSessionResult unavailableAccountResult(UserAccount account, Instant now) {
        if (account.status() == AccountStatus.LOCKED && now.isBefore(account.lockedUntil())) {
            return new CreateSessionResult.AccountLocked(account.lockedUntil());
        }
        if (!account.canStartSession(now)) {
            return new CreateSessionResult.AccountBlocked(account.status());
        }
        return null;
    }

    private List<DeviceSummary> activeDeviceSummaries(CreateSessionCommand command) {
        return devices.findActiveByUserId(command.userId()).stream()
                .map(device -> new DeviceSummary(device.id(), device.deviceName(),
                        device.platform(), device.lastSeenAt()))
                .toList();
    }

    private DeviceRegistration upsertDevice(
            CreateSessionCommand command, DeviceRegistration existing, Instant now) {
        DeviceId id = existing == null ? new DeviceId(ids.generate()) : existing.id();
        Instant createdAt = existing == null ? now : existing.createdAt();
        return new DeviceRegistration(id, command.userId(), command.platform(), command.installationId(),
                command.deviceName(), command.fcmTokenCiphertext(), command.fcmTokenFingerprint(),
                command.notificationsEnabled(), now, null, createdAt, now);
    }

    private void revokeEarlierSessions(CreateSessionCommand command, DeviceRegistration device, Instant now) {
        sessions.findActiveByUserId(command.userId(), now).stream()
                .filter(session -> device.id().equals(session.deviceId()))
                .forEach(session -> sessions.findByIdAndUserIdForUpdate(session.id(), command.userId())
                        .ifPresent(locked -> SessionRevocation.revoke(
                                locked, now, "new_login", sessions, tokens)));
    }
}
