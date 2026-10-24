package com.hyped.app.common.security.integrity;

import com.hyped.app.common.api.ApiProblemWriter;
import com.hyped.app.common.security.ApiBoundaryFilter.BodyRequest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public final class IntegrityFilter extends OncePerRequestFilter {
    private final IntegrityProperties properties;
    private final IntegrityVerifier verifier;
    private final IntegrityReplayStore replay;
    private final ApiProblemWriter problems;
    private final MeterRegistry metrics;
    private final Clock clock;

    public IntegrityFilter(IntegrityProperties properties, IntegrityVerifier verifier, IntegrityReplayStore replay,
            ApiProblemWriter problems, MeterRegistry metrics, Clock clock) {
        this.properties = properties;
        this.verifier = verifier;
        this.replay = replay;
        this.problems = problems;
        this.metrics = metrics;
        this.clock = clock;
    }

    public static boolean protectedRequest(String method, String path) {
        return path.startsWith("/api/v1/") && !Set.of("GET", "HEAD", "OPTIONS").contains(method)
                && !path.startsWith("/api/v1/auth/") && !path.startsWith("/api/v1/public/")
                && !path.equals("/api/v1/me/deletion-requests");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (properties.mode() == IntegrityProperties.Mode.DISABLED
                || !(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()
                || !protectedRequest(request.getMethod(), request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        // Bound and distributed per verified session, before spending Google API quota.
        try {
            if (replay != null && !replay.allow(hash(jwt.getToken().getSubject() + "\n"
                    + jwt.getToken().getClaimAsString("sid")), clock.instant())) {
                response.setHeader("Retry-After", "60");
                problems.write(request, response, 429, "RATE_LIMITED", "Too many attempts", "Try again later.");
                return;
            }
        } catch (RuntimeException unavailable) {
            if (properties.mode() == IntegrityProperties.Mode.ENFORCE) {
                deny(request, response, IntegrityVerifier.Outcome.UNAVAILABLE);
                return;
            }
        }
        IntegrityVerifier.Outcome outcome;
        String evidence = request.getHeader("X-Play-Integrity");
        if (evidence == null || evidence.isBlank()) {
            outcome = IntegrityVerifier.Outcome.MISSING;
        } else {
            try {
                String id = request.getHeader("X-Integrity-Id");
                if (id == null || !UUID.fromString(id).toString().equals(id)) {
                    throw new IllegalArgumentException("Invalid request binding");
                }
                String timestamp = request.getHeader("X-Integrity-Time");
                long time = Long.parseLong(timestamp);
                if (!Long.toString(time).equals(timestamp)) {
                    throw new IllegalArgumentException("Invalid request binding");
                }
                byte[] body = request instanceof BodyRequest buffered ? buffered.body()
                        : request.getInputStream().readNBytes(262145);
                String expected = binding(request.getMethod(), request.getRequestURI(), request.getQueryString(),
                        body, request.getHeader("Authorization"), id, timestamp,
                        request.getHeader("Idempotency-Key"), request.getHeader("If-Match"));
                outcome = verifier == null ? IntegrityVerifier.Outcome.UNAVAILABLE
                        : verifier.verify(evidence, expected, time);
                if (!(request instanceof BodyRequest)) {
                    request = new BodyRequest(request, body);
                }
            } catch (RuntimeException invalid) {
                outcome = IntegrityVerifier.Outcome.INVALID;
            }
        }
        if (metrics != null) {
            metrics.counter("hyped.integrity.decisions", "mode", properties.mode().name(),
                    "outcome", outcome.name()).increment();
        }
        if (properties.mode() == IntegrityProperties.Mode.ENFORCE && outcome != IntegrityVerifier.Outcome.VALID) {
            deny(request, response, outcome);
            return;
        }
        chain.doFilter(request, response);
    }

    private void deny(HttpServletRequest request, HttpServletResponse response, IntegrityVerifier.Outcome outcome)
            throws IOException {
        boolean unavailable = outcome == IntegrityVerifier.Outcome.UNAVAILABLE;
        problems.write(request, response, unavailable ? 503 : 403,
                unavailable ? "INTEGRITY_UNAVAILABLE" : "INTEGRITY_REQUIRED", "App verification unavailable",
                "Could not verify this app request. Please try again using the official Android app.");
    }

    public static String binding(String method, String path, String query, byte[] body, String authorization,
            String id, String time, String idempotency, String revision) {
        return hash(String.join("\n", "hyped-integrity-v1", method, path, value(query),
                IntegrityVerifier.digest(body), hash(value(authorization)), id, time,
                hash(value(idempotency)), hash(value(revision))));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String hash(String value) {
        return IntegrityVerifier.digest(value.getBytes(StandardCharsets.UTF_8));
    }
}
