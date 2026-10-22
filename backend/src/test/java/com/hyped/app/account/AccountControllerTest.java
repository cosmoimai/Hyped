package com.hyped.app.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyped.app.common.api.ApiProblemWriter;
import com.hyped.app.common.api.GlobalProblemHandler;
import com.hyped.app.common.api.RequestContextFilter;
import com.hyped.app.common.security.SecurityConfiguration;
import com.hyped.app.common.security.SecurityProblemHandlers;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.account.api.AccountController;
import com.hyped.app.account.application.AccountService;
import com.hyped.app.notification.infrastructure.PushWorkerSecurity;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AccountController.class, properties = {"hyped.tokens.enabled=true",
        "hyped.account-deletion.enabled=true", "hyped.push.scheduler-audience=https://scheduler.test",
        "hyped.push.scheduler-email=scheduler@test.iam.gserviceaccount.com"})
@Import({PushWorkerSecurity.class, SecurityConfiguration.class, ApiProblemWriter.class, GlobalProblemHandler.class,
        RequestContextFilter.class, SecurityProblemHandlers.class})
class AccountControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    AccountService service;
    @MockitoBean
    UserAccountRepository accounts;
    @MockitoBean
    JwtDecoder decoder;
    @MockitoBean
    Clock clock;
    private final UUID user = UUID.randomUUID();
    private final UUID session = UUID.randomUUID();
    private final UUID installation = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(decoder.decode("access")).thenReturn(Jwt.withTokenValue("access").header("alg", "RS256")
                .subject(user.toString()).claim("sid", session.toString()).claim("did", installation.toString())
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(3600)).build());
        when(accounts.findById(any())).thenReturn(Optional.of(new UserAccount(new UserId(user), AccountStatus.ACTIVE,
                null, 0, "key", null, null, null, NOW, NOW)));
    }

    @Test
    void profileReturnsOnlyApprovedFieldsAndUsesVerifiedSession() throws Exception {
        when(service.profile(user)).thenReturn(new AccountService.Profile(user, "Mira", null, 1,
                new AccountService.Limits(1, 5, 0, 10, 0, 25)));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer access"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.displayName").value("Mira"))
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(jsonPath("$.providerSubject").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
        verify(service).requireSession(user, session, installation);
    }
    @Test
    void deletionRejectsClientOwnershipAndMissingAuthentication() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/me/deletion-requests").header("Authorization", "Bearer access")
                        .header("Idempotency-Key", "key").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recentFirebaseIdToken\":\"proof\",\"userId\":\"foreign\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test
    void deletionUsesOnlyAuthenticatedActorAndReturnsIrreversibleAcceptance() throws Exception {
        when(service.request(user, "proof")).thenReturn(new AccountService.Accepted(
                "DELETION_PENDING", true, NOW.plusSeconds(86400)));
        mvc.perform(post("/api/v1/me/deletion-requests").header("Authorization", "Bearer access")
                        .header("Idempotency-Key", "key").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recentFirebaseIdToken\":\"proof\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.irreversible").value(true));
        verify(service).request(user, "proof");
    }    @Test
    void accountCleanupWorkerRejectsMobileTokensWhenPushIsDisabled() throws Exception {
        mvc.perform(post("/internal/jobs/tick").header("Authorization", "Bearer access"))
                .andExpect(status().isUnauthorized());
    }

}
