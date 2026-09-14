package com.hyped.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class HypedApplicationTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void applicationStartsAndFlywayMigratesAnEmptyDatabase() {
    assertThat(jdbcTemplate.queryForList("""
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = 'app' AND table_type = 'BASE TABLE'
        """, String.class))
        .contains("app_user", "user_identity", "device_registration",
            "auth_session", "refresh_token_record");
  }

  @Test
  void installationCanBeRegisteredWithoutNotificationPermission() {
    UUID userId = insertActiveUser();
    UUID deviceId = UUID.randomUUID();

    assertThat(insertDevice(deviceId, userId, null, null, false)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT fcm_token_ciphertext IS NULL AND fcm_token_fingerprint IS NULL
          AND NOT notifications_enabled
        FROM app.device_registration WHERE id = ?
        """, Boolean.class, deviceId)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void registrationRejectsOnlyOneFcmField(boolean ciphertextOnly) {
    UUID userId = insertActiveUser();
    byte[] ciphertext = ciphertextOnly ? new byte[] {1, 2, 3} : null;
    byte[] fingerprint = ciphertextOnly ? null : new byte[32];

    assertThatThrownBy(() -> insertDevice(
        UUID.randomUUID(), userId, ciphertext, fingerprint, false))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("device_registration_fcm_pair_check");
  }

  @Test
  void notificationsRequireFcmRegistration() {
    UUID userId = insertActiveUser();

    assertThatThrownBy(() -> insertDevice(UUID.randomUUID(), userId, null, null, true))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("device_registration_notifications_check");
  }

  @Test
  void sessionCannotUseAnotherUsersDevice() {
    UUID userId = insertActiveUser();
    UUID otherUserId = insertActiveUser();
    UUID deviceId = UUID.randomUUID();
    insertDevice(deviceId, otherUserId, null, null, false);

    assertThatThrownBy(() -> jdbcTemplate.update("""
        INSERT INTO app.auth_session
          (id, user_id, device_id, token_family_id, issued_at, last_used_at,
          expires_at, created_at, updated_at)
        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP + INTERVAL '30 days', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """, UUID.randomUUID(), userId, deviceId, UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auth_session_user_device_fk");
  }

  private UUID insertActiveUser() {
    UUID userId = UUID.randomUUID();
    jdbcTemplate.update("""
        INSERT INTO app.app_user (id, status, created_at, updated_at)
        VALUES (?, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """, userId);
    return userId;
  }

  private int insertDevice(UUID deviceId, UUID userId, byte[] ciphertext,
      byte[] fingerprint, boolean notificationsEnabled) {
    return jdbcTemplate.update("""
        INSERT INTO app.device_registration
          (id, user_id, platform, installation_id, device_name,
          fcm_token_ciphertext, fcm_token_fingerprint, notifications_enabled,
          last_seen_at, created_at, updated_at)
        VALUES (?, ?, 'android', ?, 'Test device', ?, ?, ?,
            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """, deviceId, userId, UUID.randomUUID(), ciphertext,
        fingerprint, notificationsEnabled);
  }
}
