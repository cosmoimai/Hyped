package com.hyped.app.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.domain.AuthSession;
import com.hyped.app.identity.domain.DeviceId;
import com.hyped.app.identity.domain.DevicePlatform;
import com.hyped.app.identity.domain.DeviceRegistration;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.TokenFamilyId;
import com.hyped.app.identity.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SessionStateVerifierTest {
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final DeviceRegistrationRepository devices = mock(DeviceRegistrationRepository.class);
    private final Instant now = Instant.parse("2026-10-24T12:00:00Z");
    private final UserId user = new UserId(UUID.randomUUID());
    private final SessionId sid = new SessionId(UUID.randomUUID());
    private final DeviceId device = new DeviceId(UUID.randomUUID());
    private final InstallationId installation = new InstallationId(UUID.randomUUID());
    private final SessionStateVerifier verifier = new SessionStateVerifier(
            sessions, devices, Clock.fixed(now, ZoneOffset.UTC));

    private Jwt jwt(String did) {
        return Jwt.withTokenValue("private").header("alg", "RS256").subject(user.toString())
                .claim("sid", sid.toString()).claim("did", did).build();
    }

    private AuthSession session(boolean revoked, Instant expires) {
        return new AuthSession(sid, user, device, new TokenFamilyId(UUID.randomUUID()),
                now.minusSeconds(60), now.minusSeconds(60), expires,
                revoked ? now : null, revoked ? "revoked" : null, now.minusSeconds(60), now);
    }

    private void setup(boolean revoked, Instant expires, boolean invalidated) {
        when(sessions.findByIdAndUserId(sid, user)).thenReturn(Optional.of(session(revoked, expires)));
        when(devices.findByIdAndUserId(device, user)).thenReturn(Optional.of(new DeviceRegistration(
                device, user, DevicePlatform.ANDROID, installation, "Android", null, null, false,
                now, invalidated ? now : null, now.minusSeconds(60), now)));
    }

    @Test
    void verifiedActiveSessionAndInstallationAreRequired() {
        setup(false, now.plusSeconds(60), false);
        assertThat(verifier.active(jwt(installation.toString()))).isTrue();
        assertThat(verifier.active(jwt(UUID.randomUUID().toString()))).isFalse();
    }

    @Test
    void revokedExpiredAndInvalidatedDevicesLoseAccessImmediately() {
        setup(true, now.plusSeconds(60), false);
        assertThat(verifier.active(jwt(installation.toString()))).isFalse();
        setup(false, now, false);
        assertThat(verifier.active(jwt(installation.toString()))).isFalse();
        setup(false, now.plusSeconds(60), true);
        assertThat(verifier.active(jwt(installation.toString()))).isFalse();
    }

    @Test
    void foreignOrMissingSessionCannotBorrowAnotherIdentity() {
        when(sessions.findByIdAndUserId(sid, user)).thenReturn(Optional.empty());
        assertThat(verifier.active(jwt(installation.toString()))).isFalse();
    }
}
