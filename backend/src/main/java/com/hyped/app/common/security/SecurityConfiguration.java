package com.hyped.app.common.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "true")
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            SecurityProblemHandlers problemHandlers,
            AccountStateFilter accountStateFilter,
            BearerTokenResolver bearerTokenResolver) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/exchange", "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/system/health",
                                "/actuator/health",
                                "/actuator/health/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                        .authenticated()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver)
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers)
                        .jwt(withDefaults()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .addFilterAfter(accountStateFilter, BearerTokenAuthenticationFilter.class)
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
