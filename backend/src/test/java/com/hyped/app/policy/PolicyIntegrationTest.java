package com.hyped.app.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.account.application.AccountException;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.policy.application.PolicyCatalog;
import com.hyped.app.policy.application.PolicyService;
import com.hyped.app.room.application.RoomService;
import com.hyped.app.room.domain.RoomId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PolicyIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired PolicyService policies;
    @Autowired PolicyCatalog catalog;
    @Autowired RoomService rooms;
    @Autowired JdbcTemplate jdbc;

    @Test
    void newAccountHasNoImpliedConsentAndCannotCreate() {
        var user = user();
        assertThat(policies.status(user).currentAccepted()).isFalse();
        assertThatThrownBy(() -> create(user)).isInstanceOf(AccountException.class)
                .extracting("code").isEqualTo("CURRENT_POLICY_ACCEPTANCE_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.room WHERE owner_user_id=?", Integer.class,
                user.value())).isZero();
    }
    @Test
    void internalJoinCannotBypassConsent() {
        var owner = user();
        accept(owner);
        RoomId room = create(owner);
        var member = user();
        assertThatThrownBy(() -> rooms.addMember(room, member)).isInstanceOf(AccountException.class)
                .extracting("code").isEqualTo("CURRENT_POLICY_ACCEPTANCE_REQUIRED");
        accept(member);
        rooms.addMember(room, member);
        assertThat(rooms.get(member, room)).isNotNull();
    }
    @Test
    void acceptanceUsesServerTimeAndRetryPreservesFirstTimestamp() {
        var user = user();
        Instant before = Instant.now().minusSeconds(1);
        var first = accept(user);
        var retry = accept(user);
        assertThat(first.currentAccepted()).isTrue();
        assertThat(first.accepted().adultAffirmed()).isTrue();
        assertThat(first.accepted().acceptedAt()).isAfter(before).isBefore(Instant.now().plusSeconds(1));
        assertThat(retry.accepted().acceptedAt()).isEqualTo(first.accepted().acceptedAt());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.user_policy_acceptance WHERE user_id=?",
                Integer.class, user.value())).isEqualTo(1);
        assertThat(create(user)).isNotNull();
    }
    @Test
    void previousVersionsRequireNewAcceptanceAndKeepHistory() {
        var user = user();
        jdbc.update("""
                INSERT INTO app.user_policy_acceptance VALUES (?,?, '2026-09-14','2026-09-14','2026-09-14',
                TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), user.value());
        var status = policies.status(user);
        assertThat(status.currentAccepted()).isFalse();
        assertThat(status.accepted().versions().termsVersion()).isEqualTo("2026-09-14");
        assertThatThrownBy(() -> policies.accept(user, status.accepted().versions(), true, true, true))
                .isInstanceOf(AccountException.class).extracting("code").isEqualTo("POLICY_VERSION_CHANGED");
        accept(user);
        assertThat(policies.status(user).currentAccepted()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.user_policy_acceptance WHERE user_id=?",
                Integer.class, user.value())).isEqualTo(2);
    }
    @Test
    void everyChoiceMustBeExplicit() {
        var user = user();
        for (boolean[] flags : new boolean[][] {{false, true, true}, {true, false, true}, {true, true, false}}) {
            assertThatThrownBy(() -> policies.accept(user, catalog.required(), flags[0], flags[1], flags[2]))
                    .isInstanceOf(AccountException.class).extracting("code").isEqualTo("EXPLICIT_CONSENT_REQUIRED");
        }
        assertThat(policies.status(user).accepted()).isNull();
    }
    @Test
    void draftDocumentsAreReadableButCannotAuthorizeConsent() {
        var draft = new PolicyCatalog("2026-10-22", "2026-10-22", "2026-10-22", false, "", "");
        assertThat(draft.available()).isFalse();
        var text = draft.document("privacy", "2026-10-22");
        assertThat(text.published()).isFalse();
        assertThat(text.content()).contains("Contact details pending publication").doesNotContain("{{contact}}");
        assertThatThrownBy(() -> draft.document("privacy", "../private")).isInstanceOf(AccountException.class);
        assertThatThrownBy(() -> new PolicyCatalog("2026-10-22", "2026-10-22", "2026-10-22", true, "", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAcceptanceHasOneRecord() throws Exception {
        var user = user();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return accept(user);
            });
            var second = executor.submit(() -> {
                start.await();
                return accept(user);
            });
            start.countDown();
            assertThat(first.get().accepted().acceptedAt()).isEqualTo(second.get().accepted().acceptedAt());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.user_policy_acceptance WHERE user_id=?",
                    Integer.class, user.value())).isEqualTo(1);
        } finally { jdbc.update("DELETE FROM app.app_user WHERE id=?", user.value()); }
    }
    private UserId user() {
        var user = new UserId(UUID.randomUUID());
        jdbc.update("INSERT INTO app.app_user(id,status,created_at,updated_at) VALUES (?,'active',now(),now())",
                user.value());
        return user;
    }
    private PolicyService.Status accept(UserId user) {
        return policies.accept(user, catalog.required(), true, true, true);
    }
    private RoomId create(UserId user) {
        return rooms.create(user, new RoomService.CreateRoomCommand("Trip", LocalDate.of(2040, 1, 1), LocalTime.NOON,
                "UTC", null, null)).room().id();
    }
}
