package com.hyped.app.notification.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "hyped.push", name = "enabled", havingValue = "true")
public class PushWorkerSecurity {
    @Bean
    @Order(0)
    SecurityFilterChain pushWorkerSecurityFilterChain(HttpSecurity http,
            @Value("${hyped.push.scheduler-audience}") String audience,
            @Value("${hyped.push.scheduler-email}") String email) throws Exception {
        if (audience.isBlank() || email.isBlank()) {
            throw new IllegalStateException("Scheduler audience and service-account identity are required");
        }
        var decoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("https://accounts.google.com"), identity(audience, email)));
        return http.securityMatcher("/internal/**").csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, "/internal/jobs/tick")
                        .authenticated().anyRequest().denyAll())
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.decoder(decoder))).build();
    }

    public static OAuth2TokenValidator<Jwt> identity(String audience, String email) {
        return jwt -> jwt.getAudience().size() == 1 && jwt.getAudience().contains(audience)
                && email.equals(jwt.getClaimAsString("email")) && Boolean.TRUE.equals(jwt.getClaim("email_verified"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }
}
