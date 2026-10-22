package com.hyped.app.policy;

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
import com.hyped.app.policy.api.PolicyController;
import com.hyped.app.policy.application.PolicyCatalog;
import com.hyped.app.policy.application.PolicyService;
import com.hyped.app.account.application.AccountService;

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

@WebMvcTest(controllers = PolicyController.class, properties = "hyped.tokens.enabled=true")
@Import({PolicyCatalog.class, SecurityConfiguration.class, ApiProblemWriter.class, GlobalProblemHandler.class,
        RequestContextFilter.class, SecurityProblemHandlers.class})
class PolicyControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    AccountService sessions;
    @MockitoBean
    PolicyService service;
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

    private static final String BODY = """
            {"termsVersion":"2026-10-22","privacyVersion":"2026-10-22","contentRulesVersion":"2026-10-22",
             "accepted":true,"privacyAccepted":true,"adultAffirmed":true}
            """;
    @Test
    void publicDocumentsNeedNoSignInAndContainNoIdentity() throws Exception {
        mvc.perform(get("/api/v1/public/policies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.required.privacyVersion").value("2026-10-22"))
                .andExpect(jsonPath("$.userId").doesNotExist());
        mvc.perform(get("/api/v1/public/legal/terms/2026-10-22")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Terms of Service and Content Rules"));
    }
    @Test
    void statusRequiresAuthenticationAndLiveVerifiedSession() throws Exception {
        mvc.perform(get("/api/v1/me/policy-status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/policy-status").header("Authorization", "Bearer access"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"));
        verify(sessions).requireSession(user, session, installation);
        verify(service).status(new UserId(user));
    }
    @Test
    void acceptanceUsesVerifiedOwnerAndExplicitChoicesOnly() throws Exception {
        mvc.perform(post("/api/v1/me/policy-acceptances").header("Authorization", "Bearer access")
                .header("Idempotency-Key", "attempt").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        verify(service).accept(new UserId(user), new PolicyCatalog.Versions("2026-10-22", "2026-10-22", "2026-10-22"),
                true, true, true);
    }
    @Test
    void clientOwnershipAndTimestampAreRejected() throws Exception {
        for (String forbidden : new String[] {"userId", "acceptedAt"}) {
            mvc.perform(post("/api/v1/me/policy-acceptances").header("Authorization", "Bearer access")
                    .header("Idempotency-Key", "attempt").contentType(MediaType.APPLICATION_JSON)
                    .content(BODY.trim().replace("}", ",\"" + forbidden + "\":\"forged\"}")))
                    .andExpect(status().isBadRequest());
        }
    }
    @Test
    void revokedSessionCannotAccept() throws Exception {
        org.mockito.Mockito.doThrow(new com.hyped.app.account.application.AccountException(401, "SESSION_EXPIRED",
                "Sign in again.")).when(sessions).requireSession(user, session, installation);
        mvc.perform(post("/api/v1/me/policy-acceptances").header("Authorization", "Bearer access")
                .header("Idempotency-Key", "attempt").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(service);
    }
}
