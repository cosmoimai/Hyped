package com.hyped.app.identity.infrastructure.security;

import com.hyped.app.identity.application.model.IssuedAccessToken;
import com.hyped.app.identity.application.port.out.AccessTokenIssuer;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

final class Rs256AccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final TokenProperties properties;

    Rs256AccessTokenIssuer(JwtEncoder encoder, Clock clock, TokenProperties properties) {
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.properties = Objects.requireNonNull(properties, "properties");
        if (!properties.enabled()) {
            throw new IllegalArgumentException("Token signing must be explicitly enabled");
        }
    }

    @Override
    public IssuedAccessToken issue(UserId userId, SessionId sessionId, InstallationId installationId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(installationId, "installationId");
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(properties.accessTokenLifetime());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim("sid", sessionId.toString())
                .claim("did", installationId.toString())
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").keyId(properties.keyId()).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, issuedAt, expiresAt);
    }
}
