package com.hyped.app.common.security;

import static org.springframework.security.config.Customizer.withDefaults;

import com.hyped.app.common.api.ApiProblemWriter;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@org.springframework.boot.context.properties.EnableConfigurationProperties(
        com.hyped.app.common.security.integrity.IntegrityProperties.class)
public class SecurityConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "true")
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            SecurityProblemHandlers problemHandlers,
            UserAccountRepository accounts,
            ApiProblemWriter problems,
            BearerTokenResolver bearerTokenResolver,
            org.springframework.beans.factory.ObjectProvider<SessionStateVerifier> sessions,
            com.hyped.app.common.security.integrity.IntegrityProperties integrity,
            org.springframework.beans.factory.ObjectProvider<
                    com.hyped.app.common.security.integrity.IntegrityVerifier> verifier,
            org.springframework.beans.factory.ObjectProvider<
                    com.hyped.app.common.security.integrity.IntegrityReplayStore> replay,
            org.springframework.beans.factory.ObjectProvider<io.micrometer.core.instrument.MeterRegistry> metrics,
            org.springframework.beans.factory.ObjectProvider<java.time.Clock> clocks) throws Exception {
        AccountStateFilter accountStateFilter = new AccountStateFilter(accounts, problems, sessions.getIfAvailable());
        return http
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/exchange", "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/devices/*/revoke")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/public/policies", "/api/v1/public/legal/*/*",
                                "/api/v1/system/health",
                                "/actuator/health",
                                "/actuator/health/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/invitations/preview",
                                "/api/v1/public/room-codes/preview")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/rooms/*/invitation")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/rooms/*/invitation/rotate",
                                "/api/v1/invitations/join", "/api/v1/room-codes/join")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/sessions")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/sessions/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/rooms", "/api/v1/rooms/*",
                                "/api/v1/rooms/*/event-theme", "/api/v1/rooms/*/members")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/rooms", "/api/v1/rooms/*/ownership-transfer",
                                "/api/v1/rooms/*/leave", "/api/v1/rooms/*/restore")
                        .authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/rooms/*", "/api/v1/rooms/*/event-theme",
                                "/api/v1/rooms/*/members/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/rooms/*", "/api/v1/rooms/*/members/*",
                                "/api/v1/rooms/*/permanent")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/rooms/*/reminder-preferences")
                        .authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/rooms/*/reminder-preferences")
                        .authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/devices/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/devices/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/me/policy-status").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/me/policy-acceptances").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/me", "/api/v1/me/deletion-readiness").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/me/deletion-requests").authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver)
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers)
                        .jwt(withDefaults()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .addFilterAfter(accountStateFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new com.hyped.app.common.security.integrity.IntegrityFilter(
                        integrity, verifier.getIfAvailable(), replay.getIfAvailable(), problems,
                        metrics.getIfAvailable(), clocks.getIfAvailable(java.time.Clock::systemUTC)),
                        AccountStateFilter.class)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain disabledTokenSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/system/health",
                                "/actuator/health",
                                "/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "true")
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String token = delegate.resolve(request);
            if (token != null && token.length() > 8192) {
                throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_token");
            }
            return token;
        };
    }
}
