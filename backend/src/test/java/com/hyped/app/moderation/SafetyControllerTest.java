package com.hyped.app.moderation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.hyped.app.moderation.api.SafetyController;
import com.hyped.app.moderation.application.SafetyService;

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

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@WebMvcTest(controllers = SafetyController.class, properties = "hyped.tokens.enabled=true")
@Import({
    SecurityConfiguration.class,
    ApiProblemWriter.class,
    GlobalProblemHandler.class,
    RequestContextFilter.class,
    SecurityProblemHandlers.class
})
class SafetyControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SafetyService safety;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean Clock clock;
    final UserId actor = new UserId(UUID.randomUUID());
    final UUID room = UUID.randomUUID();
    final UUID target = UUID.randomUUID();
    final Instant now = Instant.now();

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(now);
        when(decoder.decode("fixture"))
                .thenReturn(
                        Jwt.withTokenValue("fixture")
                                .header("alg", "RS256")
                                .subject(actor.value().toString())
                                .issuedAt(now.minusSeconds(10))
                                .expiresAt(now.plusSeconds(300))
                                .claim("scope", "hyped")
                                .build());
        when(accounts.findById(actor))
                .thenReturn(
                        Optional.of(
                                new UserAccount(
                                        actor,
                                        AccountStatus.ACTIVE,
                                        null,
                                        0,
                                        null,
                                        null,
                                        null,
                                        null,
                                        now,
                                        now)));
    }

    @Test
    void jwtActorOwnsReportsAndResponsesExposeOnlyReceiptFields() throws Exception {
        UUID id = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        when(safety.report(eq(actor), eq(key), any()))
                .thenReturn(new SafetyService.Receipt(id, "OPEN"));
        mvc.perform(
                        post("/api/v1/reports")
                                .header("Authorization", "Bearer fixture")
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"roomId\":\""
                                                + room
                                                + "\",\"subjectType\":\"ROOM\",\"reasonCode\":\"SPAM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reporterUserId").doesNotExist())
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test
    void rejectsSpoofedOwnershipAndRequiresAuthentication() throws Exception {
        mvc.perform(
                        post("/api/v1/reports")
                                .header("Authorization", "Bearer fixture")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reporterUserId\":\"" + target + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/me/blocks")).andExpect(status().isUnauthorized());
        mvc.perform(
                        put("/api/v1/me/blocks/" + target)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(safety);
    }

    @Test
    void blockAndUnblockUseOnlyTheVerifiedActorAndSafeCounts() throws Exception {
        String key = UUID.randomUUID().toString();
        when(safety.block(actor, target, room, key))
                .thenReturn(new SafetyService.BlockResult(1, 2));
        mvc.perform(
                        put("/api/v1/me/blocks/" + target)
                                .header("Authorization", "Bearer fixture")
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"roomId\":\"" + room + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removedCount").value(1))
                .andExpect(jsonPath("$.leftCount").value(2))
                .andExpect(jsonPath("$.roomIds").doesNotExist());
        mvc.perform(delete("/api/v1/me/blocks/" + target).header("Authorization", "Bearer fixture"))
                .andExpect(status().isNoContent());
        verify(safety).unblock(actor, target);
    }

    @Test
    void ownBlockListHasNoReverseOrSecurityFields() throws Exception {
        when(safety.blocks(actor, null))
                .thenReturn(
                        new SafetyService.BlockPage(
                                List.of(new SafetyService.BlockView(target, "Other member", now)),
                                null));
        mvc.perform(get("/api/v1/me/blocks").header("Authorization", "Bearer fixture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].displayName").value("Other member"))
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].blockedBy").doesNotExist());
    }
}
