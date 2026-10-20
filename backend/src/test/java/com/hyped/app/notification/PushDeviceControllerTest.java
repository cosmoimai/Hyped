package com.hyped.app.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.hyped.app.notification.api.PushDeviceController;
import com.hyped.app.notification.application.PushRegistrationService;
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

@WebMvcTest(controllers = PushDeviceController.class, properties = {"hyped.tokens.enabled=true",
        "hyped.push.enabled=true", "hyped.push.scheduler-audience=https://scheduler.test",
        "hyped.push.scheduler-email=scheduler@test.iam.gserviceaccount.com"})
@Import({SecurityConfiguration.class, PushWorkerSecurity.class, ApiProblemWriter.class, GlobalProblemHandler.class,
        RequestContextFilter.class, SecurityProblemHandlers.class})
class PushDeviceControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    PushRegistrationService registrations;
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
    void registrationUsesJwtOwnershipAndReturnsNoToken() throws Exception {
        mvc.perform(put("/api/v1/devices/" + installation).header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isNoContent());
        verify(registrations).register(user, session, installation, "private-token", true);
    }

    @Test
    void rejectsClientSuppliedUserId() throws Exception {
        mvc.perform(put("/api/v1/devices/" + installation).header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body().replace("{", "{\"userId\":\"" + UUID.randomUUID() + "\",")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(registrations);
    }

    @Test
    void rejectsAnotherInstallationAndSignedOutRequests() throws Exception {
        mvc.perform(put("/api/v1/devices/" + UUID.randomUUID()).header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/devices/" + installation).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(registrations);
    }

    @Test
    void removalUsesCurrentSession() throws Exception {
        mvc.perform(delete("/api/v1/devices/" + installation).header("Authorization", "Bearer access"))
                .andExpect(status().isNoContent());
        verify(registrations).register(user, session, installation, null, false);
    }

    @Test
    void mobileAccessTokenCannotInvokeInternalWorker() throws Exception {
        // The separate Google OIDC decoder never delegates to the mobile decoder.
        mvc.perform(post("/internal/jobs/tick").header("Authorization", "Bearer access"))
                .andExpect(status().isUnauthorized());
    }

    private String body() {
        return """
                {"platform":"ANDROID","fcmToken":"private-token","notificationsEnabled":true,"appVersion":"1.0"}
                """;
    }
}
