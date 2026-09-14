package com.hyped.app.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyped.app.identity.application.model.IssuedAccessToken;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserId;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class Rs256AccessTokenIssuerTest {

    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00.123456Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UserId USER = new UserId(UUID.randomUUID());
    private static final SessionId SESSION = new SessionId(UUID.randomUUID());
    private static final InstallationId INSTALLATION = new InstallationId(UUID.randomUUID());

    @Test
    void signsWithRs256AndOnlyTheAllowedClaims() throws Exception {
        TokenProperties properties = TestTokenKeys.properties();
        Rs256AccessTokenIssuer issuer = issuer(properties);
        IssuedAccessToken issued = issuer.issue(USER, SESSION, INSTALLATION);
        Jwt verified = decoder().decode(issued.tokenValue());
        SignedJWT parsed = SignedJWT.parse(issued.tokenValue());

        assertThat(parsed.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(parsed.getHeader().getKeyID()).isEqualTo("test-key-1");
        assertThat(parsed.getJWTClaimsSet().getClaims()).containsOnlyKeys(
                "sub", "sid", "did", "jti", "iss", "aud", "iat", "exp");
        assertThat(verified.getSubject()).isEqualTo(USER.toString());
        assertThat(verified.getClaimAsString("sid")).isEqualTo(SESSION.toString());
        assertThat(verified.getClaimAsString("did")).isEqualTo(INSTALLATION.toString());
        assertThat(verified.getIssuer().toString()).isEqualTo(properties.issuer());
        assertThat(verified.getAudience()).containsExactly(properties.audience());
        assertThat(UUID.fromString(verified.getId()).toString()).isEqualTo(verified.getId());
        assertThat(verified.getClaims()).doesNotContainKeys("email", "displayName", "roomId", "rooms", "name");
        assertThat(issued.toString()).doesNotContain(issued.tokenValue());
    }

    @Test
    void expiryIsExactlyOneHourAfterIssuanceAndEveryJwtHasUniqueId() throws Exception {
        Rs256AccessTokenIssuer issuer = issuer(TestTokenKeys.properties());
        IssuedAccessToken first = issuer.issue(USER, SESSION, INSTALLATION);
        IssuedAccessToken second = issuer.issue(USER, SESSION, INSTALLATION);
        Jwt verified = decoder().decode(first.tokenValue());

        assertThat(first.issuedAt()).isEqualTo(Instant.parse("2026-09-15T10:00:00Z"));
        assertThat(Duration.between(first.issuedAt(), first.expiresAt())).isEqualTo(Duration.ofHours(1));
        assertThat(verified.getIssuedAt()).isEqualTo(first.issuedAt());
        assertThat(verified.getExpiresAt()).isEqualTo(first.expiresAt());
        assertThat(decoder().decode(second.tokenValue()).getId()).isNotEqualTo(verified.getId());
    }

    private static Rs256AccessTokenIssuer issuer(TokenProperties properties) throws Exception {
        return new Rs256AccessTokenIssuer(new TokenConfiguration().jwtEncoder(properties), CLOCK, properties);
    }

    private static NimbusJwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) TestTokenKeys.PAIR.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(CLOCK);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestampValidator,
                new JwtIssuerValidator(TestTokenKeys.properties().issuer())));
        return decoder;
    }
}
