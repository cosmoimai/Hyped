package com.hyped.app.invitation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyped.app.identity.application.port.out.IdentityLookupProtector;
import com.hyped.app.identity.application.port.out.IdentityTokenVerifier;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.application.port.out.PersonalDataKeyManager;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.invitation.application.InvitationException;
import com.hyped.app.invitation.application.InvitationGuard;
import com.hyped.app.invitation.application.InvitationResolver;
import com.hyped.app.invitation.application.InvitationService;
import com.hyped.app.invitation.application.InvitationService.Share;
import com.hyped.app.invitation.application.port.out.InvitationCryptography;
import com.hyped.app.invitation.application.port.out.InvitationRepository;
import com.hyped.app.membership.application.JoinRoomService;
import com.hyped.app.room.application.RoomService;
import com.hyped.app.room.application.port.out.RoomRepository;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.RoomId;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"hyped.tokens.enabled=true", "hyped.invitation.enabled=true",
        "hyped.invitation.attempts-per-minute=10000"})
@AutoConfigureMockMvc
@Testcontainers
@Import(InvitationIntegrationTest.Config.class)
class InvitationIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    private static final Path[] KEYS = keys();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("hyped.tokens.issuer", () -> "https://issuer.example.test");
        registry.add("hyped.tokens.audience", () -> "hyped-mobile");
        registry.add("hyped.tokens.key-id", () -> "test-key");
        registry.add("hyped.tokens.public-key", () -> KEYS[0].toUri().toString());
        registry.add("hyped.tokens.private-key", () -> KEYS[1].toUri().toString());
        registry.add("hyped.invitation.public-base-url", () -> "https://invites.example.test");
        registry.add("hyped.invitation.encryption-key", () -> InvitationCipherTest.key(1));
        registry.add("hyped.invitation.lookup-hmac-key", () -> InvitationCipherTest.key(2));
    }

    @MockitoBean
    private IdentityTokenVerifier identityVerifier;
    @MockitoBean
    private IdentityLookupProtector identityLookup;
    @MockitoBean
    private PersonalDataKeyManager keyManager;
    @MockitoBean
    private PersonalDataCipher personalData;
    @MockitoSpyBean
    private InvitationCryptography crypto;
    @Autowired
    private InvitationService invitations;
    @Autowired
    private InvitationResolver resolver;
    @Autowired
    private JoinRoomService joins;
    @Autowired
    private RoomService roomService;
    @Autowired
    private RoomRepository rooms;
    @Autowired
    private InvitationRepository invitationRepository;
    @Autowired
    private InvitationGuard guard;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JwtEncoder encoder;
    @Autowired
    private MutableClock clock;

    @BeforeEach
    void resetData() {
        reset(crypto);
        clock.now = NOW;
        jdbc.update("DELETE FROM ops.idempotency_record");
        jdbc.update("DELETE FROM app.room");
        jdbc.update("DELETE FROM app.app_user");
        when(personalData.decrypt(any(), any(), any(), any()))
                .thenAnswer(invocation -> "Aarav".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void migrationCreatesUniqueProtectedCredentialsAndRoomCreationProvisionsThem() throws Exception {
        UserId owner = user();
        RoomId room = room(owner);
        Share share = invitations.get(owner, room);
        assertThat(share).isEqualTo(invitations.get(owner, room));
        assertThat(share.generation()).isEqualTo(1);
        assertThat(share.roomCode()).matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}");
        assertThat(share.inviteUrl()).matches("https://invites.example.test/invite/[A-Za-z0-9_-]{43}");
        assertThat(jdbc.queryForObject("SELECT success FROM flyway.flyway_schema_history WHERE version = '6'",
                Boolean.class)).isTrue();
        var stored = invitationRepository.find(room).orElseThrow();
        assertThat(stored.roomCodeHmac()).hasSize(32);
        assertThat(new String(stored.roomCodeCiphertext(), StandardCharsets.UTF_8))
                .doesNotContain(share.roomCode().replace("-", ""));
        assertThat(stored.linkTokenHash()).hasSize(32);
        assertThat(stored.linkTokenHash()).isNotEqualTo(MessageDigest.getInstance("SHA-256")
                .digest(token(share).getBytes(StandardCharsets.US_ASCII)));
        RoomId other = room(user());
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE app.room_invitation SET room_code_hmac = ? WHERE room_id = ?
                """, stored.roomCodeHmac(), other.value())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE app.room_invitation SET link_token_hash = ? WHERE room_id = ?
                """, stored.linkTokenHash(), other.value())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void legacyRoomGetsOneCurrentInvitationOnFirstAuthorizedRetrieval() {
        UserId owner = user();
        RoomId room = room(owner);
        jdbc.update("DELETE FROM app.room_invitation WHERE room_id = ?", room.value());
        assertThat(invitations.get(owner, room)).isEqualTo(invitations.get(owner, room));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.room_invitation WHERE room_id = ?",
                Integer.class, room.value())).isEqualTo(1);
    }

    @Test
    void safePublicPreviewMatchesAllowlistAndDoesNotReadProviderPhoto() throws Exception {
        UserId owner = user();
        RoomId room = room(owner);
        Share share = invitations.get(owner, room);
        JsonNode preview = json(publicPreview(share.roomCode().toLowerCase(Locale.ROOT), true)
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.inviter.displayName").value("Aarav")));
        assertThat(preview.properties().stream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("previewReference", "expiresAt", "event", "inviter",
                        "memberCount", "requiresAuthentication");
        assertThat(preview.get("event").properties().stream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("title", "eventAt", "eventTimeZone", "theme");
        assertThat(preview.get("inviter").properties().stream().map(Map.Entry::getKey).toList())
                .containsExactly("displayName");
        assertThat(preview.toString()).doesNotContain(owner.toString(), room.toString(),
                "private place", "private note",
                "providerPhoto", "email", share.roomCode(), token(share));
        publicPreview(token(share), false).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "bad", "OOOOOOOO", "11111111", "ABCDEFG2", "ABCD-EFGHI", "A BCD234"})
    void malformedAndUnknownCodesHaveTheSameSafeResponse(String code) throws Exception {
        publicPreview(code, true).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void missingMalformedAndUnknownLinkRequestsAreSafe() throws Exception {
        mvc.perform(post("/api/v1/public/room-codes/preview")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
        mvc.perform(post("/api/v1/public/room-codes/preview")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        for (String token : List.of("bad", crypto.newToken())) {
            publicPreview(token, false).andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
        }
    }

    @Test
    void invitationAuthorizationMatrixUsesCurrentMembershipAndOwnerRole() throws Exception {
        UserId owner = user();
        UserId cohost = user();
        UserId member = user();
        UserId outsider = user();
        RoomId room = room(owner);
        roomService.addMember(room, cohost);
        roomService.addMember(room, member);
        roomService.changeRole(owner, room, cohost, MembershipRole.CO_HOST);
        for (UserId actor : List.of(owner, cohost, member)) {
            mvc.perform(get(path(room)).header("Authorization", bearer(actor)))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        }
        mvc.perform(get(path(room)).header("Authorization", bearer(outsider))).andExpect(status().isNotFound());
        for (UserId actor : List.of(cohost, member, outsider)) {
            mvc.perform(post(path(room) + "/rotate").header("Authorization", bearer(actor))
                            .header("Idempotency-Key", "rotation"))
                    .andExpect(status().is(actor.equals(outsider) ? 404 : 403));
        }
        mvc.perform(post(path(room) + "/rotate").header("Authorization", bearer(owner))
                        .header("Idempotency-Key", "rotation"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.generation").value(2));
    }

    @Test
    void rotationAtomicallyInvalidatesBothCredentialsAndIssuedReferencesAndReplaysOnce() {
        UserId owner = user();
        RoomId room = room(owner);
        Share before = invitations.get(owner, room);
        String reference = preview(before);
        Share after = invitations.rotate(owner, room, "rotate-1");
        assertThat(invitations.rotate(owner, room, "rotate-1")).isEqualTo(after);
        assertThat(after.generation()).isEqualTo(2);
        assertThat(after.roomCode()).isNotEqualTo(before.roomCode());
        assertThat(after.inviteUrl()).isNotEqualTo(before.inviteUrl());
        expectCode(() -> resolver.preview(before.roomCode(), true), "INVITATION_INVALID");
        expectCode(() -> resolver.preview(token(before), false), "INVITATION_INVALID");
        expectCode(() -> joins.joinByPreviewReference(user(), reference, true, "join"), "INVITATION_INVALID");
        assertThat(resolver.preview(after.roomCode(), true).memberCount()).isEqualTo(1);
    }

    @Test
    void removedInvitationAndUnknownCodeAreIndistinguishable() {
        UserId owner = user();
        RoomId room = room(owner);
        Share before = invitations.get(owner, room);
        String reference = preview(before);
        jdbc.update("DELETE FROM app.room_invitation WHERE room_id = ?", room.value());
        expectCode(() -> resolver.preview(before.roomCode(), true), "INVITATION_INVALID");
        expectCode(() -> resolver.preview("ABCDEFG2", true), "INVITATION_INVALID");
        expectCode(() -> joins.joinByPreviewReference(user(), reference, true, "join"), "INVITATION_INVALID");
    }

    @Test
    void collisionRetriesSucceedWithoutPoisoningThePostgresTransaction() {
        UserId owner = user();
        RoomId first = room(owner);
        RoomId second = room(user());
        String collision = invitations.get(owner, first).roomCode().replace("-", "");
        UserId secondOwner = roomsOwner(second);
        doReturn(collision, "ABCDEFG2").when(crypto).newCode();
        Share changed = invitations.rotate(secondOwner, second, "rotate");
        assertThat(changed.roomCode()).isEqualTo("ABCD-EFG2");
        assertThat(changed.generation()).isEqualTo(2);
        assertThat(invitations.get(owner, first).roomCode().replace("-", "")).isEqualTo(collision);
    }

    @Test
    void rotationRetriesIfRandomGeneratorRepeatsEitherCurrentCredential() {
        UserId owner = user();
        RoomId room = room(owner);
        Share original = invitations.get(owner, room);
        doReturn(original.roomCode().replace("-", ""), "ABCDEFG2").when(crypto).newCode();
        Share changed = invitations.rotate(owner, room, "rotate-code");
        assertThat(changed.roomCode()).isEqualTo("ABCD-EFG2");
        reset(crypto);
        doReturn(token(changed), "a".repeat(43)).when(crypto).newToken();
        Share changedAgain = invitations.rotate(owner, room, "rotate-link");
        assertThat(token(changedAgain)).isEqualTo("a".repeat(43));
        expectCode(() -> resolver.preview(token(changed), false), "INVITATION_INVALID");
    }

    @Test
    void collisionExhaustionRestoresOldCredentialsAndRollsBackNewRoom() {
        UserId owner = user();
        RoomId first = room(owner);
        UserId secondOwner = user();
        RoomId second = room(secondOwner);
        Share original = invitations.get(secondOwner, second);
        org.mockito.Mockito.clearInvocations(crypto);
        doReturn(invitations.get(owner, first).roomCode().replace("-", "")).when(crypto).newCode();
        expectCode(() -> invitations.rotate(secondOwner, second, "rotate"), "INVITATION_UNAVAILABLE");
        verify(crypto, times(5)).newCode();
        assertThat(invitations.get(secondOwner, second)).isEqualTo(original);
        UserId thirdOwner = user();
        expectCode(() -> room(thirdOwner), "INVITATION_UNAVAILABLE");
        assertThat(rooms.countActiveOwned(thirdOwner)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void httpJoinUsesVerifiedJwtAssignsMemberAndIsIdempotent(boolean roomCode) throws Exception {
        UserId owner = user();
        UserId member = user();
        RoomId room = room(owner);
        Share share = invitations.get(owner, room);
        String reference = json(publicPreview(roomCode ? share.roomCode() : token(share), roomCode)
                .andExpect(status().isOk())).get("previewReference").asText();
        httpJoin(member, reference, roomCode, "join-1").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(room.toString()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.memberCount").value(2));
        httpJoin(member, reference, roomCode, "join-1").andExpect(status().isCreated());
        httpJoin(member, reference, roomCode, "join-2").andExpect(status().isOk());
        assertCounts(room, 2);
        assertThat(jdbc.queryForObject("SELECT joined_via FROM app.room_member WHERE room_id = ? AND user_id = ?",
                String.class, room.value(), member.value())).isEqualTo(roomCode ? "room_code" : "invite_link");
    }

    @Test
    void capacityAllowsTwentyFifthRejectsTwentySixthAndAlreadyMemberSucceedsWhenFull() {
        UserId owner = user();
        RoomId room = room(owner);
        fill(room, 23);
        String reference = preview(invitations.get(owner, room));
        UserId last = user();
        assertThat(joins.joinByPreviewReference(last, reference, true, "join").created()).isTrue();
        expectCode(() -> joins.joinByPreviewReference(user(), reference, true, "join"), "ROOM_FULL");
        assertThat(joins.joinByPreviewReference(last, reference, true, "again").created()).isFalse();
        assertThat(joins.joinByPreviewReference(owner, reference, true, "owner").room().role())
                .isEqualTo(MembershipRole.OWNER);
        assertCounts(room, 25);
    }

    @Test
    void joinedLimitAllowsTwentyFifthExcludesOwnedAndArchivedRoomsAndChecksIdempotencyFirst() {
        UserId member = user();
        room(member);
        for (int index = 0; index < 24; index++) {
            roomService.addMember(room(user()), member);
        }
        UserId owner = user();
        RoomId target = room(owner);
        String reference = preview(invitations.get(owner, target));
        joins.joinByPreviewReference(member, reference, true, "join");
        assertThat(joins.joinByPreviewReference(member, reference, true, "again").created()).isFalse();
        UserId other = user();
        RoomId extra = room(other);
        String extraReference = preview(invitations.get(other, extra));
        expectCode(() -> joins.joinByPreviewReference(member, extraReference, true, "extra"),
                "JOINED_ROOM_LIMIT_REACHED");
        assertCounts(extra, 1);
        roomService.archive(owner, target);
        assertThat(joins.joinByPreviewReference(member, extraReference, true, "extra-after-archive").created())
                .isTrue();
        assertThat(rooms.countActiveJoined(member)).isEqualTo(25);
    }

    @Test
    void concurrentJoinsAtCapacityHaveExactlyOneWinner() throws Exception {
        UserId owner = user();
        RoomId room = room(owner);
        fill(room, 23);
        String reference = preview(invitations.get(owner, room));
        UserId first = user();
        UserId second = user();
        assertThat(race(() -> joinOutcome(first, reference, "one"), () -> joinOutcome(second, reference, "two")))
                .containsExactlyInAnyOrder("created", "ROOM_FULL");
        assertCounts(room, 25);
    }

    @Test
    void concurrentDuplicateRequestsCreateOneMembership() throws Exception {
        UserId owner = user();
        UserId member = user();
        RoomId room = room(owner);
        String reference = preview(invitations.get(owner, room));
        assertThat(race(() -> joinOutcome(member, reference, "one"), () -> joinOutcome(member, reference, "two")))
                .containsExactlyInAnyOrder("created", "existing");
        assertCounts(room, 2);
    }

    @Test
    void concurrentSameKeyReplaysTheOriginalResult() throws Exception {
        UserId owner = user();
        UserId member = user();
        RoomId room = room(owner);
        String reference = preview(invitations.get(owner, room));
        assertThat(race(() -> joinOutcome(member, reference, "same"), () -> joinOutcome(member, reference, "same")))
                .contains("created")
                .allMatch(value -> value.equals("created") || value.equals("REQUEST_IN_PROGRESS"));
        assertCounts(room, 2);
    }

    @Test
    void inProgressDuplicateReturnsConflictWithRetryAfterAndCannotCreateSecondMembership() throws Exception {
        UserId owner = user();
        UserId member = user();
        RoomId room = room(owner);
        String reference = preview(invitations.get(owner, room));
        CountDownLatch pending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> tx().execute(status -> {
                var result = joins.joinByPreviewReference(member, reference, true, "same");
                pending.countDown();
                await(release);
                return result;
            }));
            await(pending);
            try {
                httpJoin(member, reference, true, "same").andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("REQUEST_IN_PROGRESS"))
                        .andExpect(header().string("Retry-After", "1"));
            } finally {
                release.countDown();
            }
            assertThat(first.get(20, TimeUnit.SECONDS).created()).isTrue();
        } finally {
            release.countDown();
        }
        assertCounts(room, 2);
        httpJoin(member, reference, true, "same").andExpect(status().isCreated());
    }

    @Test
    void terminalRejectionIsReplayedUntilTheCallerStartsANewCommand() {
        UserId owner = user();
        UserId member = user();
        RoomId room = room(owner);
        roomService.addMember(room, member);
        expectCode(() -> invitations.rotate(member, room, "rotate"), "ROOM_ACTION_FORBIDDEN");
        roomService.transfer(owner, room, member);
        expectCode(() -> invitations.rotate(member, room, "rotate"), "ROOM_ACTION_FORBIDDEN");
        assertThat(invitations.rotate(member, room, "new-command").generation()).isEqualTo(2);
    }

    @Test
    void concurrentCrossRoomJoinsCannotExceedAccountLimit() throws Exception {
        UserId member = user();
        for (int index = 0; index < 24; index++) {
            roomService.addMember(room(user()), member);
        }
        UserId owner = user();
        RoomId first = room(owner);
        RoomId second = room(owner);
        String one = preview(invitations.get(owner, first));
        String two = preview(invitations.get(owner, second));
        assertThat(race(() -> joinOutcome(member, one, "one"), () -> joinOutcome(member, two, "two")))
                .containsExactlyInAnyOrder("created", "JOINED_ROOM_LIMIT_REACHED");
        assertThat(rooms.countActiveJoined(member)).isEqualTo(25);
    }

    @Test
    void rotationWinningRoomLockInvalidatesWaitingPreviewAndJoin() throws Exception {
        UserId owner = user();
        UserId member = user();
        RoomId room = room(owner);
        Share share = invitations.get(owner, room);
        String reference = preview(share);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var rotate = executor.submit(() -> tx().execute(status -> {
                guard.lockActor(owner);
                rooms.findForUpdate(room).orElseThrow();
                locked.countDown();
                await(release);
                return invitations.rotate(owner, room, "rotate");
            }));
            await(locked);
            var join = executor.submit(() -> joinOutcome(member, reference, "join"));
            var preview = executor.submit(() -> outcome(() -> resolver.preview(share.roomCode(), true)));
            release.countDown();
            assertThat(rotate.get(20, TimeUnit.SECONDS).generation()).isEqualTo(2);
            assertThat(join.get(20, TimeUnit.SECONDS)).isEqualTo("INVITATION_INVALID");
            assertThat(preview.get(20, TimeUnit.SECONDS)).isEqualTo("INVITATION_INVALID");
        } finally {
            release.countDown();
        }
        assertCounts(room, 1);
    }

    @Test
    void joinWinningRoomLockCommitsBeforeRotationAndMembershipSurvives() throws Exception {
        UserId owner = user();
        UserId member = user();
        RoomId room = room(owner);
        String reference = preview(invitations.get(owner, room));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var join = executor.submit(() -> tx().execute(status -> {
                guard.lockActor(member);
                rooms.findForUpdate(room).orElseThrow();
                locked.countDown();
                await(release);
                return joins.joinByPreviewReference(member, reference, true, "join");
            }));
            await(locked);
            var rotate = executor.submit(() -> invitations.rotate(owner, room, "rotate"));
            release.countDown();
            assertThat(join.get(20, TimeUnit.SECONDS).created()).isTrue();
            assertThat(rotate.get(20, TimeUnit.SECONDS).generation()).isEqualTo(2);
        } finally {
            release.countDown();
        }
        assertCounts(room, 2);
        expectCode(() -> joins.joinByPreviewReference(member, reference, true, "again"), "INVITATION_INVALID");
    }

    @Test
    void previewWinningRoomLockCanReturnReferenceWhichSubsequentRotationInvalidates() throws Exception {
        UserId owner = user();
        RoomId room = room(owner);
        Share share = invitations.get(owner, room);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var preview = executor.submit(() -> tx().execute(status -> {
                rooms.findForUpdate(room).orElseThrow();
                locked.countDown();
                await(release);
                return resolver.preview(share.roomCode(), true);
            }));
            await(locked);
            var rotate = executor.submit(() -> invitations.rotate(owner, room, "rotate"));
            release.countDown();
            String reference = preview.get(20, TimeUnit.SECONDS).previewReference();
            rotate.get(20, TimeUnit.SECONDS);
            expectCode(() -> joins.joinByPreviewReference(user(), reference, true, "join"), "INVITATION_INVALID");
        } finally {
            release.countDown();
        }
    }

    @Test
    void ownershipTransferAndJoinFollowAccountThenRoomOrderWithoutDeadlock() throws Exception {
        UserId owner = user();
        UserId successor = user();
        RoomId room = room(owner);
        roomService.addMember(room, successor);
        String reference = preview(invitations.get(owner, room));
        assertThat(race(() -> joinOutcome(successor, reference, "join"),
                () -> outcome(() -> roomService.transfer(owner, room, successor))))
                .containsExactlyInAnyOrder("existing", "success");
        assertCounts(room, 2);
        assertThat(roomsOwner(room)).isEqualTo(successor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"archived", "deleting", "elapsed"})
    void endedRoomsRejectPreviewJoinAndManagement(String state) {
        UserId owner = user();
        RoomId room = room(owner);
        Share share = invitations.get(owner, room);
        String reference = preview(share);
        if (state.equals("elapsed")) {
            jdbc.update("UPDATE app.room SET event_at = ? WHERE id = ?", java.sql.Timestamp.from(NOW), room.value());
        } else {
            roomService.archive(owner, room);
            if (state.equals("deleting")) {
                jdbc.update("UPDATE app.room SET status = 'deleting' WHERE id = ?", room.value());
            }
        }
        expectCode(() -> resolver.preview(share.roomCode(), true), "ROOM_ENDED");
        expectCode(() -> joins.joinByPreviewReference(user(), reference, true, "join"), "ROOM_ENDED");
        expectCode(() -> invitations.get(owner, room), "ROOM_ENDED");
        expectCode(() -> invitations.rotate(owner, room, "rotate"), "ROOM_ENDED");
        assertCounts(room, 1);
    }

    @Test
    void expiredTamperedAndWrongChannelReferencesCannotJoin() {
        UserId owner = user();
        UserId member = user();
        RoomId room = room(owner);
        String reference = preview(invitations.get(owner, room));
        expectCode(() -> joins.joinByPreviewReference(member, reference, false, "wrong"), "INVITATION_INVALID");
        byte[] tampered = Base64.getUrlDecoder().decode(reference);
        tampered[tampered.length - 1] ^= 1;
        expectCode(() -> joins.joinByPreviewReference(member,
                Base64.getUrlEncoder().withoutPadding().encodeToString(tampered), true, "tamper"),
                "INVITATION_INVALID");
        clock.now = NOW.plusSeconds(600);
        expectCode(() -> joins.joinByPreviewReference(member, reference, true, "expired"), "PREVIEW_EXPIRED");
        assertCounts(room, 1);
    }

    @Test
    void rollbackRestoresMembershipCountsAndReplayRecords() {
        UserId owner = user();
        UserId member = user();
        RoomId room = room(owner);
        String reference = preview(invitations.get(owner, room));
        assertThatThrownBy(() -> tx().execute(status -> {
            joins.joinByPreviewReference(member, reference, true, "join");
            throw new IllegalStateException("rollback probe");
        })).isInstanceOf(IllegalStateException.class);
        assertCounts(room, 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ops.idempotency_record", Integer.class)).isZero();
        assertThat(joins.joinByPreviewReference(member, reference, true, "join").created()).isTrue();
        assertCounts(room, 2);
    }

    @Test
    void idempotencyKeysCannotBeReusedAcrossDifferentRoomRequests() {
        UserId owner = user();
        UserId member = user();
        RoomId first = room(owner);
        RoomId second = room(owner);
        String one = preview(invitations.get(owner, first));
        String two = preview(invitations.get(owner, second));
        joins.joinByPreviewReference(member, one, true, "same");
        expectCode(() -> joins.joinByPreviewReference(member, two, true, "same"), "IDEMPOTENCY_KEY_REUSED");
        invitations.rotate(owner, first, "same");
        expectCode(() -> invitations.rotate(owner, second, "same"), "IDEMPOTENCY_KEY_REUSED");
        assertCounts(second, 1);
    }

    @Test
    void crossUserAndCrossRoomInputCannotOverrideTheJwtOrReference() throws Exception {
        UserId owner = user();
        UserId actor = user();
        UserId victim = user();
        RoomId room = room(owner);
        RoomId other = room(victim);
        String reference = preview(invitations.get(owner, room));
        for (Map<String, String> body : List.of(
                Map.of("previewReference", reference, "userId", victim.toString()),
                Map.of("previewReference", reference, "roomId", other.toString()))) {
            mvc.perform(post("/api/v1/room-codes/join").header("Authorization", bearer(actor))
                            .header("Idempotency-Key", "join").contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get(path(other)).header("Authorization", bearer(actor))).andExpect(status().isNotFound());
        assertCounts(room, 1);
        assertCounts(other, 1);
    }

    @Test
    void missingInvalidAndExpiredRealJwtsCannotJoinOrManageInvitations() throws Exception {
        UserId owner = user();
        RoomId room = room(owner);
        String reference = preview(invitations.get(owner, room));
        for (String credential : List.of("", "Bearer invalid", "Bearer " + jwt(owner, NOW.minusSeconds(120)))) {
            for (String endpoint : List.of("/api/v1/room-codes/join", "/api/v1/invitations/join",
                    path(room) + "/rotate")) {
                mvc.perform(post(endpoint).header("Authorization", credential).header("Idempotency-Key", "key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(Map.of("previewReference", reference))))
                        .andExpect(status().isUnauthorized());
            }
            mvc.perform(get(path(room)).header("Authorization", credential)).andExpect(status().isUnauthorized());
        }
        assertCounts(room, 1);
    }

    @Test
    void validJwtShapeWithTamperedSignatureCannotJoin() throws Exception {
        UserId owner = user();
        RoomId room = room(owner);
        String reference = preview(invitations.get(owner, room));
        String valid = jwt(user(), NOW.plusSeconds(3600));
        String[] parts = valid.split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 1;
        String forged = parts[0] + "." + parts[1] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        mvc.perform(post("/api/v1/room-codes/join").header("Authorization", "Bearer " + forged)
                        .header("Idempotency-Key", "join").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("previewReference", reference))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCESS_TOKEN_INVALID"));
        assertCounts(room, 1);
    }

    @Test
    void deletedRoomRejectsPreviouslyIssuedReferenceAndCascadesInvitation() {
        UserId owner = user();
        RoomId room = room(owner);
        String reference = preview(invitations.get(owner, room));
        jdbc.update("DELETE FROM app.room WHERE id = ?", room.value());
        expectCode(() -> joins.joinByPreviewReference(user(), reference, true, "join"), "ROOM_ENDED");
        assertThat(invitationRepository.find(room)).isEmpty();
    }

    @Test
    void waitingJoinRechecksPreviewExpiryAfterLocks() throws Exception {
        UserId owner = user();
        UserId member = user();
        RoomId room = room(owner);
        String reference = preview(invitations.get(owner, room));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> tx().execute(status -> {
                guard.lockActor(member);
                locked.countDown();
                await(release);
                clock.now = NOW.plusSeconds(600);
                return true;
            }));
            await(locked);
            var join = executor.submit(() -> joinOutcome(member, reference, "join"));
            release.countDown();
            holder.get(20, TimeUnit.SECONDS);
            assertThat(join.get(20, TimeUnit.SECONDS)).isEqualTo("PREVIEW_EXPIRED");
        } finally {
            release.countDown();
        }
        assertCounts(room, 1);
    }

    @Test
    void accountStateIsRecheckedInsideJoiningTransaction() {
        UserId owner = user();
        UserId member = user();
        RoomId room = room(owner);
        String reference = preview(invitations.get(owner, room));
        jdbc.update("UPDATE app.app_user SET status = 'deletion_pending' WHERE id = ?", member.value());
        expectCode(() -> joins.joinByPreviewReference(member, reference, true, "join"), "ACCOUNT_DELETION_PENDING");
        assertCounts(room, 1);
    }

    @Test
    void unimplementedInvitationRoutesRemainDeniedAndMutationKeysAreRequired() throws Exception {
        UserId owner = user();
        RoomId room = room(owner);
        mvc.perform(post(path(room) + "/revoke").header("Authorization", bearer(owner)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/public/invitations/preview")).andExpect(status().isUnauthorized());
        mvc.perform(post(path(room) + "/rotate").header("Authorization", bearer(owner)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private UserId user() {
        UserId id = new UserId(UUID.randomUUID());
        jdbc.update("""
                INSERT INTO app.app_user (id, status, pii_key_reference, created_at, updated_at)
                VALUES (?, 'active', 'test-reference', ?, ?)
                """, id.value(), java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO app.user_profile (user_id, display_name_ciphertext, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                """, id.value(), new byte[] {1}, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        return id;
    }

    private RoomId room(UserId owner) {
        return roomService.create(owner, new RoomService.CreateRoomCommand("Goa trip", LocalDate.of(2030, 12, 20),
                LocalTime.NOON, "Asia/Kolkata", "private place", "private note")).room().id();
    }

    private UserId roomsOwner(RoomId room) {
        return new UserId(jdbc.queryForObject("SELECT owner_user_id FROM app.room WHERE id = ?",
                UUID.class, room.value()));
    }

    private void fill(RoomId room, int additional) {
        for (int index = 0; index < additional; index++) {
            roomService.addMember(room, user());
        }
    }

    private String preview(Share share) {
        return resolver.preview(share.roomCode(), true).previewReference();
    }

    private String token(Share share) {
        return share.inviteUrl().substring(share.inviteUrl().lastIndexOf('/') + 1);
    }

    private String path(RoomId room) {
        return "/api/v1/rooms/" + room.value() + "/invitation";
    }

    private String joinOutcome(UserId actor, String reference, String key) {
        try {
            return joins.joinByPreviewReference(actor, reference, true, key).created() ? "created" : "existing";
        } catch (InvitationException exception) {
            return exception.code();
        }
    }

    private String outcome(Runnable action) {
        try {
            action.run();
            return "success";
        } catch (InvitationException exception) {
            return exception.code();
        }
    }

    private void expectCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(InvitationException.class)
                .extracting(exception -> ((InvitationException) exception).code()).isEqualTo(code);
    }

    private void assertCounts(RoomId room, int expected) {
        assertThat(jdbc.queryForObject("SELECT member_count FROM app.room WHERE id = ?",
                Integer.class, room.value())).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.room_member WHERE room_id = ?",
                Integer.class, room.value())).isEqualTo(expected);
    }

    private List<String> race(Callable<String> first, Callable<String> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> {
                await(start);
                return first.call();
            });
            var two = executor.submit(() -> {
                await(start);
                return second.call();
            });
            start.countDown();
            return List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
        }
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactions);
    }

    private ResultActions publicPreview(String credential, boolean roomCode) throws Exception {
        return mvc.perform(post(roomCode ? "/api/v1/public/room-codes/preview" : "/api/v1/public/invitations/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(roomCode ? "roomCode" : "token", credential))));
    }

    private ResultActions httpJoin(UserId actor, String reference, boolean roomCode, String key) throws Exception {
        return mvc.perform(post(roomCode ? "/api/v1/room-codes/join" : "/api/v1/invitations/join")
                .header("Authorization", bearer(actor)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("previewReference", reference))));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String bearer(UserId actor) {
        return "Bearer " + jwt(actor, NOW.plusSeconds(3600));
    }

    private String jwt(UserId actor, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().subject(actor.toString()).issuer("https://issuer.example.test")
                .audience(List.of("hyped-mobile")).id(UUID.randomUUID().toString())
                .claim("sid", UUID.randomUUID().toString()).claim("did", UUID.randomUUID().toString())
                .issuedAt(expiresAt.minusSeconds(3600)).expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-key").build(), claims)).getTokenValue();
    }

    private static Path[] keys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            Path directory = Files.createTempDirectory("hyped-invitation-keys");
            directory.toFile().deleteOnExit();
            Path[] paths = {directory.resolve("public.pem"), directory.resolve("private.pem")};
            byte[][] encoded = {pair.getPublic().getEncoded(), pair.getPrivate().getEncoded()};
            String[] labels = {"PUBLIC KEY", "PRIVATE KEY"};
            for (int index = 0; index < 2; index++) {
                Files.writeString(paths[index], "-----BEGIN " + labels[index] + "-----\n"
                        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded[index])
                        + "\n-----END " + labels[index] + "-----\n");
                paths[index].toFile().deleteOnExit();
            }
            return paths;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock invitationTestClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private volatile Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
