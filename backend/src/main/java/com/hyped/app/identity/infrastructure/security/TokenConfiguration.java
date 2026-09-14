package com.hyped.app.identity.infrastructure.security;

import com.hyped.app.identity.application.port.out.AccessTokenIssuer;
import com.hyped.app.identity.application.port.out.RefreshTokenDigester;
import com.hyped.app.identity.application.port.out.RefreshTokenGenerator;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TokenProperties.class)
public class TokenConfiguration {

    @Bean
    RefreshTokenGenerator refreshTokenGenerator(Clock clock, TokenProperties properties) {
        return new SecureRandomRefreshTokenGenerator(new SecureRandom(), clock, properties.refreshTokenLifetime());
    }

    @Bean
    RefreshTokenDigester refreshTokenDigester() {
        return new Sha256RefreshTokenDigester();
    }

    @Bean
    @ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "true")
    JwtEncoder jwtEncoder(TokenProperties properties) throws IOException {
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        try (InputStream publicInput = properties.publicKey().getInputStream();
                InputStream privateInput = properties.privateKey().getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(publicInput);
            privateKey = RsaKeyConverters.pkcs8().convert(privateInput);
        }
        if (publicKey == null || privateKey == null || publicKey.getModulus().bitLength() < 2048
                || !publicKey.getModulus().equals(privateKey.getModulus())
                || !(privateKey instanceof RSAPrivateCrtKey crtKey)
                || !publicKey.getPublicExponent().equals(crtKey.getPublicExponent())) {
            throw new IllegalArgumentException("Token signing requires a matching RSA key pair of at least 2048 bits");
        }
        RSAKey signingKey = new RSAKey.Builder(publicKey).privateKey(privateKey)
                .keyID(properties.keyId()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    @Bean
    @ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "true")
    AccessTokenIssuer accessTokenIssuer(JwtEncoder encoder, Clock clock, TokenProperties properties) {
        return new Rs256AccessTokenIssuer(encoder, clock, properties);
    }
}
