package com.hyped.app.invitation.api;

import com.hyped.app.common.api.ApiProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Local defensive limit: ten attempts/minute/socket source, shared by both preview and join routes.
 * Production ingress must additionally enforce a distributed 10/minute source/installation risk bucket
 * (security design section 17) and 5/hour rotation limit. Do not enable framework forwarded headers;
 * use only a proxy allowlist at the ingress. Source addresses are bounded, ephemeral and never logged.
 * Android App Links and Play Store fallback belong to the configured invite host, outside this API.
 * No Firebase Dynamic Links or external link-provider calls are made here.
 */
@Component
@Order(-90)
@ConditionalOnProperty(prefix = "hyped.invitation", name = "enabled", havingValue = "true")
public class InvitationAttemptFilter extends OncePerRequestFilter {
    private static final Set<String> ATTEMPTS = Set.of("/api/v1/public/invitations/preview",
            "/api/v1/public/room-codes/preview", "/api/v1/invitations/join", "/api/v1/room-codes/join");
    private final Map<String, Integer> attempts = new HashMap<>();
    private final Clock clock;
    private final ApiProblemWriter problems;
    private final int limit;
    private long window = -1;

    public InvitationAttemptFilter(Clock clock, ApiProblemWriter problems,
            @Value("${hyped.invitation.attempts-per-minute:10}") int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Invitation attempt limit must be positive");
        }
        this.clock = clock;
        this.problems = problems;
        this.limit = limit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (ATTEMPTS.contains(path) && "POST".equals(request.getMethod()) && !allow(request.getRemoteAddr())) {
            response.setHeader("Retry-After", "60");
            problems.write(request, response, 429, "RATE_LIMITED", "Too many attempts",
                    "Try again later.");
            return;
        }
        chain.doFilter(request, response);
    }

    private synchronized boolean allow(String source) {
        long current = clock.instant().getEpochSecond() / 60;
        if (current != window) {
            attempts.clear();
            window = current;
        }
        if (!attempts.containsKey(source) && attempts.size() >= 10_000) {
            return false;
        }
        int count = attempts.getOrDefault(source, 0);
        if (count >= limit) {
            return false;
        }
        attempts.put(source, count + 1);
        return true;
    }
}
