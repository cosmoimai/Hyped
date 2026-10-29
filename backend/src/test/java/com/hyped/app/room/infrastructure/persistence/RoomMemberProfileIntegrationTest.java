package com.hyped.app.room.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.application.port.out.PersonalDataKeyManager;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.application.service.AuthenticationProfileReader;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.profile.application.port.out.UserProfileRepository;
import com.hyped.app.room.application.RoomOperationException;
import com.hyped.app.room.application.RoomService;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.RoomId;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real transaction proxies and encrypted profiles reproduce the physical-device members failure. */
@SpringBootTest(properties = {"hyped.personal-data.enabled=true", "hyped.personal-data.provider=local",
        "server.address=127.0.0.1", "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("local")
@Import(RoomMemberProfileIntegrationTest.Profiles.class)
@Testcontainers(disabledWithoutDocker = true)
class RoomMemberProfileIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @TempDir
    static Path keyDirectory;

    @DynamicPropertySource
    static void localKeys(DynamicPropertyRegistry registry) throws Exception {
        Files.setPosixFilePermissions(keyDirectory, PosixFilePermissions.fromString("rwx------"));
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        Files.write(keyDirectory.resolve("wrapping-key.bin"), bytes);
        Files.setPosixFilePermissions(keyDirectory.resolve("wrapping-key.bin"),
                PosixFilePermissions.fromString("rw-------"));
        registry.add("hyped.personal-data.local-directory", () -> keyDirectory.toString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Profiles {
        @Bean
        AuthenticationProfileReader profileReader(UserAccountRepository accounts, UserProfileRepository profiles,
                PersonalDataCipher cipher) {
            return new AuthenticationProfileReader(accounts, profiles, cipher);
        }
    }

    @Autowired
    RoomService service;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PersonalDataKeyManager keys;
    @Autowired
    PersonalDataCipher cipher;
    @Autowired
    Clock clock;
    @MockitoSpyBean
    AuthenticationProfileReader profiles;

    UserId owner;
    UserId member;
    UserId outsider;
    RoomId room;

    @BeforeEach
    void fixture() {
        owner = user("Owner");
        member = user("Participant");
        outsider = user("Unrelated account");
        room = service.create(owner, new RoomService.CreateRoomCommand("Profile regression",
                LocalDate.of(2030, 12, 20), LocalTime.NOON, "UTC", null, null)).room().id();
        service.addMember(room, member);
    }

    @AfterEach
    void cleanup() {
        if (room != null) {
            jdbc.update("DELETE FROM app.room WHERE id=?", room.value());
        }
        for (UserId user : new UserId[] {owner, member, outsider}) {
            if (user != null) {
                jdbc.update("DELETE FROM app.app_user WHERE id=?", user.value());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MembershipRole.class)
    void authorizedRolesReadRealEncryptedNamesOutsideTransactions(MembershipRole role) {
        if (role == MembershipRole.CO_HOST) {
            service.changeRole(owner, room, member, role);
        }
        UserId actor = role == MembershipRole.OWNER ? owner : member;
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return call.callRealMethod();
        }).when(profiles).read(owner);
        var result = service.members(actor, room);
        assertThat(result.items()).extracting(RoomService.MemberView::displayName)
                .containsExactly("Owner", "Participant");
        assertThat(result.items()).allMatch(item -> item.photo() == null);
        assertThat(result.items()).filteredOn(RoomService.MemberView::isCurrentUser).hasSize(1);
        assertThat(result.callerRole()).isEqualTo(role);
        verify(profiles, never()).read(outsider);
    }

    @Test
    void foreignRoomAccessIsRejectedBeforeDecryptingAnyProfile() {
        assertThatThrownBy(() -> service.members(outsider, room)).isInstanceOf(RoomOperationException.class);
        verifyNoInteractions(profiles);
    }

    @Test
    void concurrentRemovalOfCallerDoesNotReturnProtectedMemberList() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            doAnswer(call -> {
                executor.submit(() -> service.removeMember(owner, room, member, null)).get(5, TimeUnit.SECONDS);
                return call.callRealMethod();
            }).when(profiles).read(owner);
            assertThatThrownBy(() -> service.members(member, room))
                    .isInstanceOf(RoomOperationException.class)
                    .extracting(error -> ((RoomOperationException) error).code()).isEqualTo("ROOM_UNAVAILABLE");
        }
    }

    @Test
    void concurrentRoleChangeReturnsRevisionConflictInsteadOfStalePermissions() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            doAnswer(call -> {
                executor.submit(() -> service.changeRole(owner, room, member, MembershipRole.CO_HOST))
                        .get(5, TimeUnit.SECONDS);
                return call.callRealMethod();
            }).when(profiles).read(owner);
            assertThatThrownBy(() -> service.members(member, room))
                    .isInstanceOf(RoomOperationException.class)
                    .extracting(error -> ((RoomOperationException) error).code()).isEqualTo("ROOM_REVISION_MISMATCH");
        }
    }

    private UserId user(String name) {
        UserId user = new UserId(UUID.randomUUID());
        String reference = keys.createKey(user);
        byte[] ciphertext = cipher.encrypt(user, reference, PersonalDataField.USER_PROFILE_DISPLAY_NAME,
                name.getBytes(StandardCharsets.UTF_8));
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO app.app_user(id,status,pii_key_reference,created_at,updated_at) VALUES (?,'active',?,?,?)
                """, user.value(), reference, now, now);
        jdbc.update("""
                INSERT INTO app.user_profile(user_id,display_name_ciphertext,created_at,updated_at) VALUES (?,?,?,?)
                """, user.value(), ciphertext, now, now);
        com.hyped.app.policy.AcceptedPolicyFixture.accept(jdbc, user.value());
        return user;
    }
}
