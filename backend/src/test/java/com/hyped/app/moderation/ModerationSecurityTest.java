package com.hyped.app.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyped.app.common.api.ApiProblemWriter;
import com.hyped.app.common.api.GlobalProblemHandler;
import com.hyped.app.common.security.SecurityConfiguration;
import com.hyped.app.common.security.SecurityProblemHandlers;
import com.hyped.app.moderation.api.ModerationController;
import com.hyped.app.moderation.application.ModerationService;
import com.hyped.app.moderation.infrastructure.ModerationSecurity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@WebMvcTest(
        controllers = ModerationController.class,
        properties = {"hyped.moderation.review-enabled=true"})
@Import({
    ModerationSecurity.class,
    SecurityConfiguration.class,
    ApiProblemWriter.class,
    SecurityProblemHandlers.class,
    GlobalProblemHandler.class
})
class ModerationSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ModerationService service;
    @MockitoBean ModerationSecurity.ReviewDecoder verifier;

    @Test
    void rejectsMobileJwtAndUnauthenticatedReviewRequests() throws Exception {
        when(verifier.decode("mobile-token")).thenThrow(new BadJwtException("Invalid token"));
        mvc.perform(get("/internal/moderation/reports")).andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/internal/moderation/reports")
                                .header("Authorization", "Bearer mobile-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void permitsSeparatelyVerifiedReviewer() throws Exception {
        when(verifier.decode("reviewer"))
                .thenReturn(token(List.of("https://review.example.test"), "reviewer@test", true));
        when(service.queue(50)).thenReturn(List.of());
        mvc.perform(get("/internal/moderation/reports").header("Authorization", "Bearer reviewer"))
                .andExpect(status().isOk());
    }

    @Test
    void exactAudienceAllowlistedVerifiedIdentityIsRequired() {
        var validate =
                ModerationSecurity.identity("https://review.example.test", Set.of("reviewer@test"));
        assertThat(
                        validate.validate(
                                        token(
                                                List.of("https://review.example.test"),
                                                "reviewer@test",
                                                true))
                                .hasErrors())
                .isFalse();
        assertThat(
                        validate.validate(
                                        token(
                                                List.of("https://foreign.test"),
                                                "reviewer@test",
                                                true))
                                .hasErrors())
                .isTrue();
        assertThat(
                        validate.validate(
                                        token(
                                                List.of("https://review.example.test", "extra"),
                                                "reviewer@test",
                                                true))
                                .hasErrors())
                .isTrue();
        assertThat(
                        validate.validate(
                                        token(
                                                List.of("https://review.example.test"),
                                                "other@test",
                                                true))
                                .hasErrors())
                .isTrue();
        assertThat(
                        validate.validate(
                                        token(
                                                List.of("https://review.example.test"),
                                                "reviewer@test",
                                                false))
                                .hasErrors())
                .isTrue();
    }

    private Jwt token(List<String> audience, String email, boolean verified) {
        return Jwt.withTokenValue("fixture")
                .header("alg", "RS256")
                .subject("reviewer-subject")
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300))
                .audience(audience)
                .claim("email", email)
                .claim("email_verified", verified)
                .build();
    }
}
