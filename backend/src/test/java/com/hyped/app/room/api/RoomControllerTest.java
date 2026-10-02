package com.hyped.app.room.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyped.app.common.api.ApiProblemException;
import com.hyped.app.common.api.ApiProblemWriter;
import com.hyped.app.common.api.GlobalProblemHandler;
import com.hyped.app.common.api.RequestContextFilter;
import com.hyped.app.common.security.SecurityConfiguration;
import com.hyped.app.common.security.SecurityProblemHandlers;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.room.application.RoomService;
import com.hyped.app.room.application.port.out.RoomRepository.AuthorizedRoom;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.Room;
import com.hyped.app.room.domain.RoomId;
import com.hyped.app.room.domain.RoomStatus;
import com.hyped.app.room.domain.RoomTheme;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RoomController.class, properties = "hyped.tokens.enabled=true")
@Import({SecurityConfiguration.class, ApiProblemWriter.class, GlobalProblemHandler.class,
        RequestContextFilter.class, SecurityProblemHandlers.class})
class RoomControllerTest {
    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final UserId USER = new UserId(UUID.fromString("019b1f20-4152-7ce8-ae9e-b12b87fe8a31"));
    private static final RoomId ROOM = new RoomId(UUID.fromString("019b1f33-e664-7ef4-985e-76b3ac298620"));
    private static final String TOKEN = "signed.valid.access.token";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RoomService service;

    @MockitoBean
    private UserAccountRepository accounts;

    @MockitoBean
    private JwtDecoder decoder;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(decoder.decode(TOKEN)).thenReturn(jwt());
        when(accounts.findById(USER)).thenReturn(Optional.of(account()));
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void createsRoomFromJwtActorAndReturnsContractFields() throws Exception {
        when(service.create(eq(USER), any())).thenReturn(room(MembershipRole.OWNER));

        mvc.perform(post("/api/v1/rooms")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .header("Idempotency-Key", "create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Goa trip","eventLocalDate":"2030-12-20",
                                 "eventLocalTime":"10:00","eventTimeZone":"Asia/Kolkata",
                                 "location":"North Goa","description":"Trip"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"room-1\""))
                .andExpect(jsonPath("$.id").value(ROOM.toString()))
                .andExpect(jsonPath("$.memberCount").value(8))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.theme.presetKey").value("soft-blue-01"))
                .andExpect(jsonPath("$.members").doesNotExist());
    }

    @Test
    void createRejectsRecurrenceAndUnsupportedThemeFields() throws Exception {
        mvc.perform(post("/api/v1/rooms")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .header("Idempotency-Key", "create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Goa trip","eventLocalDate":"2030-12-20",
                                 "eventLocalTime":"10:00","eventTimeZone":"Asia/Kolkata",
                                 "recurrence":"weekly"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/rooms")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .header("Idempotency-Key", "create-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Goa trip","eventLocalDate":"2030-12-20",
                                 "eventLocalTime":"10:00","eventTimeZone":"Asia/Kolkata",
                                 "theme":{"kind":"PRESET","presetKey":"soft-blue-01",
                                 "overlayKey":"dark-soft","mediaAssetId":"019b1f2b-d5e2-7d46-9165-a37ed59d6080"}}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listingAndDetailAreCallerScoped() throws Exception {
        when(service.list(USER, RoomStatus.ACTIVE, 20)).thenReturn(List.of(room(MembershipRole.MEMBER)));
        when(service.get(USER, ROOM)).thenReturn(room(MembershipRole.MEMBER));

        mvc.perform(get("/api/v1/rooms?status=ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].memberCount").value(8))
                .andExpect(jsonPath("$.items[0].role").value("MEMBER"));
        mvc.perform(get("/api/v1/rooms/{id}", ROOM.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"room-1\""));
    }

    @Test
    void eventThemeSubresourceReadsAndUpdatesWithRoomEtags() throws Exception {
        when(service.get(USER, ROOM)).thenReturn(room(MembershipRole.MEMBER));
        when(service.update(eq(USER), eq(ROOM), eq(1L), any())).thenReturn(room(MembershipRole.CO_HOST));

        mvc.perform(get("/api/v1/rooms/{id}/event-theme", ROOM.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"room-1\""))
                .andExpect(jsonPath("$.theme.kind").value("PRESET"));
        mvc.perform(patch("/api/v1/rooms/{id}/event-theme", ROOM.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .header(HttpHeaders.IF_MATCH, "\"room-1\"")
                        .contentType("application/merge-patch+json")
                        .content("""
                                {"theme":{"kind":"GRADIENT","presetKey":"blue-lilac-02",
                                 "overlayKey":"dark-soft"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"room-1\""));
    }

    @Test
    void updateRequiresRevisionAndMapsIsolationFailureSafely() throws Exception {
        mvc.perform(patch("/api/v1/rooms/{id}", ROOM.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType("application/merge-patch+json")
                        .content("{\"title\":\"Changed\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("ROOM_REVISION_REQUIRED"));

        when(service.get(USER, ROOM)).thenThrow(new ApiProblemException(HttpStatus.NOT_FOUND,
                "ROOM_UNAVAILABLE", "Room unavailable", "The room is unavailable."));
        mvc.perform(get("/api/v1/rooms/{id}", ROOM.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"));
    }

    @Test
    void missingInvalidAndExpiredJwtAreDenied() throws Exception {
        mvc.perform(get("/api/v1/rooms")).andExpect(status().isUnauthorized());
        when(decoder.decode("bad")).thenThrow(new BadJwtException("invalid"));
        mvc.perform(get("/api/v1/rooms").header(HttpHeaders.AUTHORIZATION, "Bearer bad"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_TOKEN_INVALID"));
        when(decoder.decode("expired")).thenThrow(new BadJwtException("expired"));
        mvc.perform(get("/api/v1/rooms").header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unimplementedMemberListAndUnknownRoutesRemainDenied() throws Exception {
        mvc.perform(get("/api/v1/rooms/{id}/members", ROOM.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/private-future").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }

    private AuthorizedRoom room(MembershipRole role) {
        return new AuthorizedRoom(new Room(ROOM, USER, "Goa trip", Instant.parse("2030-12-20T04:30:00Z"),
                "Asia/Kolkata", "North Goa", "Trip", RoomTheme.defaultTheme(ROOM, USER, NOW),
                RoomStatus.ACTIVE, 1, 8,
                null, null, NOW, NOW), role);
    }

    private Jwt jwt() {
        return new Jwt(TOKEN, NOW.minusSeconds(60), NOW.plusSeconds(3600),
                Map.of("alg", "RS256", "kid", "test-key"),
                Map.of("sub", USER.toString(), "sid", UUID.randomUUID().toString(),
                        "did", UUID.randomUUID().toString(), "iss", "https://issuer.test",
                        "aud", List.of("hyped-mobile")));
    }

    private UserAccount account() {
        return new UserAccount(USER, AccountStatus.ACTIVE, null, 0, null, null, null, null, NOW, NOW);
    }
}
