package com.hyped.app.common.security.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyped.app.common.api.ApiProblemWriter;
import java.time.Clock;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class IntegrityFilterTest {
    private final IntegrityVerifier verifier = mock(IntegrityVerifier.class);
    private final IntegrityReplayStore replay = mock(IntegrityReplayStore.class);

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse request(IntegrityProperties.Mode mode, String evidence, boolean authenticated)
            throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/rooms");
        request.addHeader("X-Integrity-Id", "00000000-0000-4000-8000-000000000001");
        request.addHeader("X-Integrity-Time", "1792843200000");
        request.addHeader("Authorization", "Bearer PRIVATE_JWT");
        request.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (evidence != null) {
            request.addHeader("X-Play-Integrity", evidence);
        }
        if (authenticated) {
            var jwt = Jwt.withTokenValue("PRIVATE_JWT").header("alg", "RS256")
                    .subject("user").claim("sid", "session").build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
        }
        when(replay.allow(any(), any())).thenReturn(true);
        var filter = new IntegrityFilter(new IntegrityProperties(mode, null, Set.of("A".repeat(43))),
                verifier, replay, new ApiProblemWriter(new ObjectMapper()), null, Clock.systemUTC());
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> res.getWriter().write("allowed"));
        return response;
    }

    @Test
    void disabledAndUnauthenticatedRequestsNeverCallVerifier() throws Exception {
        assertThat(request(IntegrityProperties.Mode.DISABLED, "evidence", true).getContentAsString())
                .isEqualTo("allowed");
        SecurityContextHolder.clearContext();
        assertThat(request(IntegrityProperties.Mode.ENFORCE, "evidence", false).getContentAsString())
                .isEqualTo("allowed");
        verifyNoInteractions(verifier);
    }

    @Test
    void observeAllowsMissingInvalidReplayAndUnavailable() throws Exception {
        assertThat(request(IntegrityProperties.Mode.OBSERVE, null, true).getContentAsString()).isEqualTo("allowed");
        for (var outcome : IntegrityVerifier.Outcome.values()) {
            when(verifier.verify(any(), any(), anyLong())).thenReturn(outcome);
            assertThat(request(IntegrityProperties.Mode.OBSERVE, "evidence", true).getContentAsString())
                    .isEqualTo("allowed");
        }
    }

    @Test
    void enforceRejectsMissingInvalidReplayAndUnavailableWithSafeErrors() throws Exception {
        assertThat(request(IntegrityProperties.Mode.ENFORCE, null, true).getStatus()).isEqualTo(403);
        for (var outcome : Set.of(IntegrityVerifier.Outcome.INVALID, IntegrityVerifier.Outcome.REPLAYED,
                IntegrityVerifier.Outcome.UNAVAILABLE)) {
            when(verifier.verify(any(), any(), anyLong())).thenReturn(outcome);
            var response = request(IntegrityProperties.Mode.ENFORCE, "evidence", true);
            assertThat(response.getStatus()).isEqualTo(outcome == IntegrityVerifier.Outcome.UNAVAILABLE ? 503 : 403);
            assertThat(response.getContentAsString()).doesNotContain("PRIVATE_JWT", "evidence", "session");
        }
    }

    @Test
    void validAuthenticatedFlowPasses() throws Exception {
        when(verifier.verify(any(), any(), anyLong())).thenReturn(IntegrityVerifier.Outcome.VALID);
        assertThat(request(IntegrityProperties.Mode.ENFORCE, "evidence", true).getContentAsString())
                .isEqualTo("allowed");
    }

    @Test
    void requestBindingChangesWithBodyIdentityRevisionAndReplayId() {
        // Shared Dart/Java contract vector covers UTF-8 and every bound header.
        assertThat(IntegrityFilter.binding("PATCH", "/api/v1/rooms/123", "page=1",
                "{\"title\":\"世界\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "Bearer fixture-access-token", "00000000-0000-4000-8000-000000000001",
                "1792843200000", "fixture-key", "\"room-2\""))
                .isEqualTo("2hxkhzjVPhT4LfhHr1pRAkhgtU050CjCQ7IZwg2_VOg");
        byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String base = IntegrityFilter.binding("POST", "/api/v1/rooms", null, body, "Bearer a", "id", "time", "k", "r");
        assertThat(base).isNotEqualTo(IntegrityFilter.binding("POST", "/api/v1/rooms", null,
                body, "Bearer b", "id", "time", "k", "r"));
        assertThat(base).isNotEqualTo(IntegrityFilter.binding("POST", "/api/v1/rooms", null,
                body, "Bearer a", "new", "time", "k", "r"));
        assertThat(base).isNotEqualTo(IntegrityFilter.binding("POST", "/api/v1/rooms", null,
                body, "Bearer a", "id", "time", "k", "new"));
        assertThat(base).isNotEqualTo(IntegrityFilter.binding("POST", "/api/v1/rooms", null,
                new byte[0], "Bearer a", "id", "time", "k", "r"));
    }

    @Test
    void publicReadsAndCleanupAlwaysRemainAccessibleWithoutAttestation() {
        assertThat(IntegrityFilter.protectedRequest("POST", "/api/v1/auth/logout")).isFalse();
        assertThat(IntegrityFilter.protectedRequest("POST", "/api/v1/me/deletion-requests")).isFalse();
        assertThat(IntegrityFilter.protectedRequest("POST", "/api/v1/public/invitations/preview")).isFalse();
        assertThat(IntegrityFilter.protectedRequest("GET", "/api/v1/rooms")).isFalse();
    }
}
