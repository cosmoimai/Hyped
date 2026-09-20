package com.hyped.app.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserId;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AccessTokenDecoderTest {
    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final TokenProperties PROPERTIES = TestTokenKeys.properties();
    private static final UserId USER_ID = new UserId(UUID.randomUUID());
    private static final SessionId SESSION_ID = new SessionId(UUID.randomUUID());
    private static final InstallationId INSTALLATION_ID = new InstallationId(UUID.randomUUID());

    @Test
    void acceptsIssuedRs256TokenWithRequiredClaims() throws Exception {
        JwtDecoder decoder = new TokenConfiguration().jwtDecoder(PROPERTIES, CLOCK);
        String token = new Rs256AccessTokenIssuer(
                new TokenConfiguration().jwtEncoder(PROPERTIES), CLOCK, PROPERTIES)
                .issue(USER_ID, SESSION_ID, INSTALLATION_ID).tokenValue();

        var decoded = decoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(decoded.getClaimAsString("sid")).isEqualTo(SESSION_ID.toString());
        assertThat(decoded.getClaimAsString("did")).isEqualTo(INSTALLATION_ID.toString());
        assertThat(decoded.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", PROPERTIES.keyId());
    }

    @Test
    void rejectsUnknownKidWrongAudienceAndMissingDeviceClaim() throws Exception {
        JwtDecoder decoder = new TokenConfiguration().jwtDecoder(PROPERTIES, CLOCK);

        assertThatThrownBy(() -> decoder.decode(token("unknown-key", PROPERTIES.audience(), true,
                SignatureAlgorithm.RS256, NOW.plusSeconds(3600))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(PROPERTIES.keyId(), "other-audience", true,
                SignatureAlgorithm.RS256, NOW.plusSeconds(3600))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(PROPERTIES.keyId(), PROPERTIES.audience(), false,
                SignatureAlgorithm.RS256, NOW.plusSeconds(3600))))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredTokenAndNonRs256Algorithm() throws Exception {
        JwtDecoder decoder = new TokenConfiguration().jwtDecoder(PROPERTIES, CLOCK);

        assertThatThrownBy(() -> decoder.decode(token(PROPERTIES.keyId(), PROPERTIES.audience(), true,
                SignatureAlgorithm.RS256, NOW.minusSeconds(61))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(PROPERTIES.keyId(), PROPERTIES.audience(), true,
                SignatureAlgorithm.RS512, NOW.plusSeconds(3600))))
                .isInstanceOf(JwtException.class);
    }

    private static String token(
            String keyId,
            String audience,
            boolean includeDevice,
            SignatureAlgorithm algorithm,
            Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(PROPERTIES.issuer())
                .audience(List.of(audience))
                .subject(USER_ID.toString())
                .claim("sid", SESSION_ID.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(NOW.minusSeconds(120))
                .expiresAt(expiresAt);
        if (includeDevice) {
            claims.claim("did", INSTALLATION_ID.toString());
        }
        return encoder(keyId, algorithm).encode(JwtEncoderParameters.from(
                JwsHeader.with(algorithm).keyId(keyId).build(), claims.build())).getTokenValue();
    }

    private static JwtEncoder encoder(String keyId, SignatureAlgorithm algorithm) {
        JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(algorithm.getName());
        RSAKey key = new RSAKey.Builder((RSAPublicKey) TestTokenKeys.PAIR.getPublic())
                .privateKey((RSAPrivateKey) TestTokenKeys.PAIR.getPrivate())
                .keyID(keyId)
                .algorithm(jwsAlgorithm)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
}
