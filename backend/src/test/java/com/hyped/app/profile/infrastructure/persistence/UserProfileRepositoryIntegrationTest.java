package com.hyped.app.profile.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.profile.application.port.out.UserProfileRepository;
import com.hyped.app.profile.domain.UserProfile;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class UserProfileRepositoryIntegrationTest {
    private static final Instant CREATED = Instant.parse("2026-09-19T10:00:00.123456Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private UserProfileRepository profiles;

    @Autowired
    private UserProfilePersistenceMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void flywayCreatesProfileTableWithoutPrematureMediaForeignKey() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT success FROM flyway.flyway_schema_history WHERE version = '4'", Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForList("""
                SELECT column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'app' AND table_name = 'user_profile'
                """))
                .extracting(row -> row.get("column_name"))
                .containsExactlyInAnyOrder("user_id", "display_name_ciphertext", "provider_photo_url_ciphertext",
                        "photo_media_id", "profile_revision", "created_at", "updated_at");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints tc
                JOIN information_schema.constraint_column_usage ccu
                    ON tc.constraint_name = ccu.constraint_name AND tc.constraint_schema = ccu.constraint_schema
                WHERE tc.table_schema = 'app' AND tc.table_name = 'user_profile'
                    AND tc.constraint_type = 'FOREIGN KEY' AND ccu.column_name = 'photo_media_id'
                """, Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT col_description('app.user_profile'::regclass, ordinal_position)
                FROM information_schema.columns
                WHERE table_schema = 'app' AND table_name = 'user_profile' AND column_name = 'photo_media_id'
                """, String.class)).contains("add its foreign key when app.media_asset is created");
        assertThat(jdbcTemplate.queryForList("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema = 'app' AND table_name = 'user_profile'
                """, String.class)).contains("user_profile_pkey", "user_profile_user_fk",
                        "user_profile_revision_positive_check", "user_profile_timestamp_order_check");

        UserId userId = insertUser();
        jdbcTemplate.update("""
                INSERT INTO app.user_profile
                    (user_id, display_name_ciphertext, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                """, userId.value(), bytes(1), Timestamp.from(CREATED), Timestamp.from(CREATED));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT profile_revision FROM app.user_profile WHERE user_id = ?", Long.class, userId.value()))
                .isEqualTo(1L);
    }

    @Test
    void savesAndLoadsOnlyCiphertextValues() {
        UserId userId = insertUser();
        UUID futureMediaId = UUID.randomUUID();
        UserProfile profile = profile(userId, bytes(1), bytes(2), futureMediaId, 1, CREATED);

        assertThat(profiles.save(profile)).isEqualTo(profile);
        entityManager.clear();

        assertThat(profiles.findByUserId(userId)).contains(profile);
        Map<String, Object> stored = jdbcTemplate.queryForMap("""
                SELECT display_name_ciphertext, provider_photo_url_ciphertext, profile_revision
                FROM app.user_profile WHERE user_id = ?
                """, userId.value());
        assertThat((byte[]) stored.get("display_name_ciphertext")).isEqualTo(bytes(1));
        assertThat((byte[]) stored.get("provider_photo_url_ciphertext")).isEqualTo(bytes(2));
        assertThat(stored.get("profile_revision")).isEqualTo(1L);
    }

    @Test
    void conditionallyUpdatesAndIncrementsRevisionAtomically() {
        UserId userId = insertUser();
        profiles.save(profile(userId, bytes(1), null, null, 1, CREATED));
        UserProfile replacement = profile(userId, bytes(2), bytes(3), UUID.randomUUID(), 1,
                CREATED.plusSeconds(1));

        UserProfile updated = profiles.update(replacement, 1).orElseThrow();
        assertThat(updated.profileRevision()).isEqualTo(2);
        assertThat(updated.displayNameCiphertext()).isEqualTo(bytes(2));
        assertThat(updated.providerPhotoUrlCiphertext()).isEqualTo(bytes(3));
        assertThat(updated.createdAt()).isEqualTo(CREATED);
        assertThat(updated.updatedAt()).isEqualTo(CREATED.plusSeconds(1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT profile_revision FROM app.user_profile WHERE user_id = ?", Long.class, userId.value()))
                .isEqualTo(2L);
    }

    @Test
    void staleRevisionReportsConflictAndDoesNotOverwriteWinner() {
        UserId userId = insertUser();
        profiles.save(profile(userId, bytes(1), null, null, 1, CREATED));
        UserProfile winner = profile(userId, bytes(2), null, null, 1, CREATED.plusSeconds(1));
        UserProfile stale = profile(userId, bytes(3), null, null, 1, CREATED.plusSeconds(2));

        assertThat(profiles.update(winner, 1)).isPresent();
        assertThat(profiles.update(stale, 1)).isEmpty();
        UserProfile stored = profiles.findByUserId(userId).orElseThrow();
        assertThat(stored.profileRevision()).isEqualTo(2);
        assertThat(stored.displayNameCiphertext()).isEqualTo(bytes(2));
        assertThat(stored.updatedAt()).isEqualTo(CREATED.plusSeconds(1));
    }

    @Test
    void copiesCiphertextAcrossDomainMapperEntityAndRepositoryBoundaries() {
        UserId userId = insertUser();
        byte[] displayName = bytes(1);
        byte[] providerPhoto = bytes(2);
        UserProfile profile = profile(userId, displayName, providerPhoto, null, 1, CREATED);
        Arrays.fill(displayName, (byte) 9);
        Arrays.fill(providerPhoto, (byte) 9);
        UserProfileEntity entity = mapper.toEntity(profile);
        entity.getDisplayNameCiphertext()[0] = 8;
        entity.getProviderPhotoUrlCiphertext()[0] = 8;
        UserProfile mapped = mapper.toDomain(entity);
        entity.setDisplayNameCiphertext(bytes(7));
        entity.setProviderPhotoUrlCiphertext(bytes(7));

        UserProfile saved = profiles.save(mapped);
        saved.displayNameCiphertext()[0] = 6;
        saved.providerPhotoUrlCiphertext()[0] = 6;
        entityManager.clear();
        UserProfile loaded = profiles.findByUserId(userId).orElseThrow();
        loaded.displayNameCiphertext()[0] = 5;
        loaded.providerPhotoUrlCiphertext()[0] = 5;
        assertThat(profiles.findByUserId(userId).orElseThrow()).isEqualTo(profile);
    }

    @Test
    void cascadesProfileWhenUserIsDeleted() {
        UserId userId = insertUser();
        profiles.save(profile(userId, bytes(1), null, null, 1, CREATED));
        jdbcTemplate.update("DELETE FROM app.app_user WHERE id = ?", userId.value());
        entityManager.clear();
        assertThat(profiles.findByUserId(userId)).isEmpty();
    }

    @Test
    void saveRejectsNonInitialRevision() {
        UserId userId = insertUser();
        assertThatThrownBy(() -> profiles.save(profile(userId, bytes(1), null, null, 2, CREATED)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class).hasMessageContaining("revision 1");
    }

    private UserId insertUser() {
        UserId userId = new UserId(UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO app.app_user (id, status, created_at, updated_at)
                VALUES (?, 'active', ?, ?)
                """, userId.value(), Timestamp.from(CREATED), Timestamp.from(CREATED));
        return userId;
    }

    private static UserProfile profile(UserId userId, byte[] displayName, byte[] providerPhoto,
            UUID photoMediaId, long revision, Instant updatedAt) {
        return new UserProfile(userId, displayName, providerPhoto, photoMediaId, revision, CREATED, updatedAt);
    }

    private static byte[] bytes(int value) {
        byte[] bytes = new byte[8];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
