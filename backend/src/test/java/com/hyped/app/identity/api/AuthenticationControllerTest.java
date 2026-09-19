package com.hyped.app.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyped.app.common.api.ApiProblemWriter;
import com.hyped.app.common.api.GlobalProblemHandler;
import com.hyped.app.common.api.RequestContextFilter;
import com.hyped.app.common.security.AccountStateFilter;
import com.hyped.app.common.security.SecurityConfiguration;
import com.hyped.app.common.security.SecurityProblemHandlers;
import com.hyped.app.identity.application.exception.IdentityTokenVerificationException;
import com.hyped.app.identity.application.model.AuthenticationExchangeResult;
import com.hyped.app.identity.application.model.AuthenticationUserProfile;
import com.hyped.app.identity.application.model.DeviceSummary;
import com.hyped.app.identity.application.model.GeneratedRefreshToken;
import com.hyped.app.identity.application.model.IssuedAccessToken;
import com.hyped.app.identity.application.model.RefreshSessionResult;
import com.hyped.app.identity.application.model.SessionTokenPair;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.application.service.AuthenticationExchangeService;
import com.hyped.app.identity.application.service.AuthenticationProfileReader;
import com.hyped.app.identity.application.service.LogoutSessionService;
import com.hyped.app.identity.application.service.RefreshSessionService;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.DeviceId;
import com.hyped.app.identity.domain.DevicePlatform;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthenticationController.class, properties = "hyped.tokens.enabled=true")
@Import({SecurityConfiguration.class, ApiProblemWriter.class, GlobalProblemHandler.class,
        RequestContextFilter.class, SecurityProblemHandlers.class, AccountStateFilter.class})
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationControllerTest {
    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");
    private static final UserId USER_ID = new UserId(UUID.fromString("019b1f20-4152-7ce8-ae9e-b12b87fe8a31"));
    private static final SessionId SESSION_ID =
            new SessionId(UUID.fromString("019b1f21-31ca-749e-b9b9-96d4c0efec40"));
    private static final UUID INSTALLATION_ID = UUID.fromString("019b1f1d-48f0-7b33-99da-4a498fe22d11");
    private static final String IDENTITY_TOKEN = "identity-token-secret";
    private static final String REFRESH_TOKEN = "refresh-token-secret-value";
    private static final String VALID_ACCESS_TOKEN = "signed.valid.access.token";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthenticationExchangeService exchangeService;

    @MockitoBean
    private RefreshSessionService refreshService;

    @MockitoBean
    private LogoutSessionService logoutService;

    @MockitoBean
    private AuthenticationProfileReader profiles;

    @MockitoBean
    private UserAccountRepository accounts;

    @MockitoBean
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        when(decoder.decode(VALID_ACCESS_TOKEN)).thenReturn(accessJwt());
        when(accounts.findById(USER_ID)).thenReturn(Optional.of(activeAccount()));
    }

    @Test
    void returnsCreatedForNewAccountWithDocumentedFields(CapturedOutput output) throws Exception {
        when(exchangeService.exchange(any())).thenReturn(
                new AuthenticationExchangeResult.Success(tokens(), USER_ID, true));
        when(profiles.read(USER_ID)).thenReturn(profile());

        mvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exchangeRequest()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").value("access-token-value"))
                .andExpect(jsonPath("$.refreshToken").value("replacement-refresh-token"))
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.user.displayName").value("Aarav"))
                .andExpect(jsonPath("$.user.profileRevision").value(1))
                .andExpect(jsonPath("$.isNewAccount").value(true));

        assertThat(output).doesNotContain("access-token-value").doesNotContain("replacement-refresh-token");
    }

    @Test
    void returnsOkForExistingAccount() throws Exception {
        when(exchangeService.exchange(any())).thenReturn(
                new AuthenticationExchangeResult.Success(tokens(), USER_ID, false));
        when(profiles.read(USER_ID)).thenReturn(profile());

        mvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exchangeRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewAccount").value(false));
    }

    @Test
    void rejectsMalformedAndInvalidExchangeInput() throws Exception {
        mvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firebaseIdToken":"","installationId":null,"platform":null,
                                 "deviceName":"","appVersion":""}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(5));
    }

    @Test
    void mapsInvalidIdentityTokenWithoutExposingIt(CapturedOutput output) throws Exception {
        when(exchangeService.exchange(any())).thenThrow(new IdentityTokenVerificationException(
                IdentityTokenVerificationException.Reason.INVALID_TOKEN));

        String response = mvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exchangeRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_TOKEN_INVALID"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(IDENTITY_TOKEN);
        assertThat(output).doesNotContain(IDENTITY_TOKEN);
    }

    @Test
    void mapsSixthDeviceAndEmailCandidate() throws Exception {
        DeviceSummary device = new DeviceSummary(new DeviceId(UUID.randomUUID()), "Pixel 10",
                DevicePlatform.ANDROID, NOW);
        when(exchangeService.exchange(any())).thenReturn(
                new AuthenticationExchangeResult.DeviceLimitReached(List.of(device)));

        mvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exchangeRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVICE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.devices[0].deviceName").value("Pixel 10"));

        when(exchangeService.exchange(any())).thenReturn(
                new AuthenticationExchangeResult.AccountLinkConfirmationRequired());
        mvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exchangeRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LINK_CONFIRMATION_REQUIRED"));
    }

    @Test
    void rotatesRefreshToken(CapturedOutput output) throws Exception {
        when(refreshService.refresh(any(), any())).thenReturn(new RefreshSessionResult.Success(tokens()));

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.refreshToken").value("replacement-refresh-token"))
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString()));

        assertThat(output).doesNotContain("access-token-value").doesNotContain("replacement-refresh-token");
    }

    @Test
    void mapsExpiredReuseAndInstallationMismatch() throws Exception {
        assertRefreshFailure("SESSION_EXPIRED");
        assertRefreshFailure("REFRESH_TOKEN_REUSE_DETECTED");
        assertRefreshFailure("SESSION_DEVICE_MISMATCH");
    }

    @Test
    void rejectsLogoutWithoutJwt() throws Exception {
        mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void logsOutCurrentJwtSession() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + VALID_ACCESS_TOKEN))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(logoutService).logout(USER_ID, SESSION_ID);
    }

    @Test
    void deniesCompromisedAccountOnProtectedRequest() throws Exception {
        when(accounts.findById(USER_ID)).thenReturn(Optional.of(account(AccountStatus.COMPROMISED)));

        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + VALID_ACCESS_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_COMPROMISED"));
    }

    @Test
    void returnsSafeProblemForInvalidAccessToken(CapturedOutput output) throws Exception {
        String rawToken = "raw-invalid-access-token";
        when(decoder.decode(rawToken)).thenThrow(new BadJwtException("invalid access token"));

        String response = mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_TOKEN_INVALID"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(rawToken).doesNotContain("invalid access token");
        assertThat(output).doesNotContain(rawToken).doesNotContain("invalid access token");
    }

    @Test
    void hidesUnexpectedProviderAndDatabaseDetails(CapturedOutput output) throws Exception {
        String unsafeDetail = "provider response and SQL select contained " + IDENTITY_TOKEN;
        when(exchangeService.exchange(any())).thenThrow(new IllegalStateException(unsafeDetail));

        String response = mvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exchangeRequest()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(unsafeDetail).doesNotContain(IDENTITY_TOKEN);
        assertThat(output).doesNotContain(unsafeDetail).doesNotContain(IDENTITY_TOKEN);
    }

    private void assertRefreshFailure(String code) throws Exception {
        when(refreshService.refresh(any(), any())).thenReturn(
                new RefreshSessionResult.ReauthenticationRequired(code));
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(code));
    }

    private static String exchangeRequest() {
        return """
                {"firebaseIdToken":"%s","installationId":"%s","platform":"ANDROID",
                 "deviceName":"Pixel 10","appVersion":"1.0.0+1"}
                """.formatted(IDENTITY_TOKEN, INSTALLATION_ID);
    }

    private static String refreshRequest() {
        return """
                {"refreshToken":"%s","installationId":"%s"}
                """.formatted(REFRESH_TOKEN, INSTALLATION_ID);
    }

    private static SessionTokenPair tokens() {
        return new SessionTokenPair(SESSION_ID,
                new IssuedAccessToken("access-token-value", NOW, NOW.plusSeconds(3600)),
                new GeneratedRefreshToken("replacement-refresh-token", NOW, NOW.plusSeconds(86400)));
    }

    private static AuthenticationUserProfile profile() {
        return new AuthenticationUserProfile(USER_ID, "Aarav", null, null, 1);
    }

    private static Jwt accessJwt() {
        return new Jwt(VALID_ACCESS_TOKEN, NOW, NOW.plusSeconds(3600),
                Map.of("alg", "RS256", "kid", "test-key"),
                Map.of("sub", USER_ID.toString(), "sid", SESSION_ID.toString(),
                        "did", INSTALLATION_ID.toString(), "jti", UUID.randomUUID().toString()));
    }

    private static UserAccount activeAccount() {
        return account(AccountStatus.ACTIVE);
    }

    private static UserAccount account(AccountStatus status) {
        return new UserAccount(USER_ID, status, null, 0, "pdk-reference",
                null, null, null, NOW.minusSeconds(60), NOW);
    }
}
