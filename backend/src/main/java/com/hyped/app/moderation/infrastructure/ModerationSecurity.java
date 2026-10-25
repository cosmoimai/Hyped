package com.hyped.app.moderation.infrastructure;

import com.hyped.app.common.security.SecurityProblemHandlers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "hyped.moderation.review-enabled", havingValue = "true")
public class ModerationSecurity {
    public record ReviewDecoder(org.springframework.security.oauth2.jwt.JwtDecoder delegate) {
        public Jwt decode(String token) {
            return delegate.decode(token);
        }
    }

    @Bean
    ReviewDecoder moderationDecoder(
            @Value("${hyped.moderation.audience:}") String audience,
            @Value("${hyped.moderation.reviewers:}") String reviewers) {
        Set<String> identities =
                Arrays.stream(reviewers.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
        if (!audience.startsWith("https://")
                || URI.create(audience).getHost() == null
                || identities.isEmpty()) {
            throw new IllegalStateException(
                    "Moderation requires an HTTPS audience and approved reviewer identities");
        }
        var decoder =
                NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                        .build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer("https://accounts.google.com"),
                        identity(audience, identities)));
        return new ReviewDecoder(decoder);
    }

    @Bean
    @Order(-1)
    SecurityFilterChain moderationSecurity(
            HttpSecurity http, SecurityProblemHandlers problems, ReviewDecoder verifier)
            throws Exception {
        return http.securityMatcher("/internal/moderation/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(
                        s ->
                                s.jwt(j -> j.decoder(verifier::decode))
                                        .authenticationEntryPoint(problems)
                                        .accessDeniedHandler(problems))
                .exceptionHandling(
                        e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .build();
    }

    public static OAuth2TokenValidator<Jwt> identity(String audience, Set<String> reviewers) {
        return jwt ->
                jwt.getAudience().size() == 1
                                && jwt.getAudience().contains(audience)
                                && Boolean.TRUE.equals(jwt.getClaim("email_verified"))
                                && reviewers.contains(jwt.getClaimAsString("email"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }
}
