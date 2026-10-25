package com.hyped.app.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.media.application.MediaException;
import com.hyped.app.media.application.MediaService;
import com.hyped.app.media.application.port.out.MediaObjects;
import com.hyped.app.media.application.port.out.PhotoModerator;
import com.hyped.app.room.application.RoomService;
import com.hyped.app.room.domain.RoomTheme;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(RoomPhotoIntegrationTest.Cloud.class)
class RoomPhotoIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MediaService media;
    @Autowired RoomService rooms;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeObjects objects;
    @Autowired Clock clock;
    static volatile PhotoModerator.Decision decision = PhotoModerator.Decision.APPROVED;

    @TestConfiguration
    static class Cloud {
        @Bean
        FakeObjects cloudObjects() {
            return new FakeObjects();
        }

        @Bean
        PhotoModerator moderator() {
            return bytes -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return decision;
            };
        }
    }

    static class FakeObjects implements MediaObjects {
        final Map<UUID, byte[]> raw = new ConcurrentHashMap<>();
        final Map<UUID, byte[]> ready = new ConcurrentHashMap<>();
        int publishes;
        boolean failDelete;

        private void outside() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        }

        @Override
        public Upload upload(UUID id, String type, int size) {
            outside();
            return new Upload("https://private.test/upload", Map.of("Content-Type", type));
        }

        @Override
        public byte[] read(UUID id, String type, int size) {
            outside();
            return raw.get(id);
        }

        @Override
        public byte[] readReady(UUID id) {
            outside();
            return ready.get(id);
        }

        @Override
        public void publish(UUID id, byte[] safe) {
            outside();
            ready.put(id, safe);
            publishes++;
        }

        @Override
        public String delivery(UUID id) {
            outside();
            return "https://private.test/delivery";
        }

        @Override
        public void delete(UUID id) {
            outside();
            if (failDelete) {
                throw new IllegalStateException("private-provider-error");
            }
            raw.remove(id);
            ready.remove(id);
        }
    }

    @BeforeEach
    void reset() {
        decision = PhotoModerator.Decision.APPROVED;
        objects.publishes = 0;
        objects.failDelete = false;
    }

    UserId user() {
        var id = new UserId(UUID.randomUUID());
        jdbc.update(
                "INSERT INTO app.app_user(id,status,created_at,updated_at)"
                        + " VALUES(?,'active',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id.value());
        com.hyped.app.policy.AcceptedPolicyFixture.accept(jdbc, id.value());
        return id;
    }

    static byte[] photo() throws Exception {
        var image =
                new java.awt.image.BufferedImage(16, 12, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "jpeg", bytes);
        return bytes.toByteArray();
    }

    static String hash(byte[] bytes) throws Exception {
        return Base64.getEncoder()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    MediaService.Authorized authorization(UserId user, byte[] bytes) throws Exception {
        return media.authorize(
                user,
                new MediaService.Request(
                        "ROOM_COVER",
                        "ignored.jpg",
                        "image/jpeg",
                        bytes.length,
                        16,
                        12,
                        hash(bytes)));
    }

    UUID upload(UserId user) throws Exception {
        byte[] bytes = photo();
        UUID id = authorization(user, bytes).mediaAssetId();
        objects.raw.put(id, bytes);
        assertThat(media.complete(user, id, hash(bytes)).status()).isEqualTo("READY");
        return id;
    }

    RoomService.CreateRoomCommand command(UUID asset) {
        return new RoomService.CreateRoomCommand(
                "Photo room",
                LocalDate.of(2040, 1, 1),
                LocalTime.NOON,
                "UTC",
                null,
                null,
                new RoomService.ThemeCommand(
                        RoomTheme.Kind.UPLOADED_IMAGE, null, "image-dark-40", asset));
    }

    @Autowired com.hyped.app.moderation.application.SafetyService safety;
    @Autowired com.hyped.app.moderation.application.ModerationService moderation;

    @Test
    void reportedPhotoCanBeReviewedAfterQuarantineExpiryAndRemovedFromEveryRoom() throws Exception {
        var owner=user();
        var first=rooms.create(owner,command(upload(owner))).room();
        UUID photo=first.theme().mediaAssetId();
        var second=rooms.create(owner,command(photo)).room();
        objects.raw.remove(photo); // Quarantine lifecycle must not break review of published content.
        var report=safety.report(owner,UUID.randomUUID().toString(),
                new com.hyped.app.moderation.application.SafetyService.ReportRequest(first.id().value(),
                        "THEME",null,"INAPPROPRIATE_CONTENT",null));
        assertThat(moderation.photo(report.reportId())).isNotEmpty();
        moderation.decide(report.reportId(),"REMOVE_PHOTO",null,"reviewer");
        assertThat(rooms.get(owner,first.id()).room().theme().mediaAssetId()).isNull();
        assertThat(rooms.get(owner,second.id()).room().theme().mediaAssetId()).isNull();
        assertThatThrownBy(()->media.delivery(owner,photo)).isInstanceOf(MediaException.class);
        assertThat(safety.invalidations(owner)).hasSize(2);
        media.cleanup();
        assertThat(objects.ready).doesNotContainKey(photo);
    }

    @Test
    void onlyValidatedModeratedOwnedPhotosCanBeAttached() throws Exception {
        UserId owner = user();
        UUID id = upload(owner);
        var room = rooms.create(owner, command(id));
        assertThat(room.room().theme().mediaAssetId()).isEqualTo(id);
        assertThat(rooms.get(owner, room.room().id()).room().theme().kind())
                .isEqualTo(RoomTheme.Kind.UPLOADED_IMAGE);
        assertThat(media.delivery(owner, id).url()).isEqualTo("https://private.test/delivery");
    }

    @Test
    void foreignOwnershipAndAccessLossAreRejectedWithoutLeaks() throws Exception {
        UserId owner = user();
        UserId member = user();
        UserId outsider = user();
        UUID id = upload(owner);
        var room = rooms.create(owner, command(id));
        rooms.addMember(room.room().id(), member);
        assertThat(media.delivery(member, id).mediaAssetId()).isEqualTo(id);
        assertThatThrownBy(() -> media.delivery(outsider, id)).isInstanceOf(MediaException.class);
        rooms.leave(member, room.room().id(), null);
        assertThatThrownBy(() -> media.delivery(member, id)).isInstanceOf(MediaException.class);
        assertThatThrownBy(() -> rooms.create(outsider, command(id)))
                .isInstanceOf(MediaException.class);
        assertThatThrownBy(() -> media.complete(outsider, id, hash(photo())))
                .isInstanceOf(MediaException.class);
    }

    @Test
    void cohostMayRetainExistingCoverButCannotAttachSomeoneElsesAsset() throws Exception {
        UserId owner = user();
        UserId cohost = user();
        UUID id = upload(owner);
        var room = rooms.create(owner, command(id));
        rooms.addMember(room.room().id(), cohost);
        jdbc.update(
                "UPDATE app.room_member SET role='co_host' WHERE room_id=? AND user_id=?",
                room.room().id().value(),
                cohost.value());
        long revision = rooms.get(owner, room.room().id()).room().revision();
        var update =
                new RoomService.UpdateRoomCommand(
                        "Edited by host",
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        false,
                        new RoomService.ThemeCommand(
                                RoomTheme.Kind.UPLOADED_IMAGE, null, "image-dark-40", id),
                        true);
        assertThat(
                        rooms.update(cohost, room.room().id(), revision, update)
                                .room()
                                .theme()
                                .mediaAssetId())
                .isEqualTo(id);
        UUID other = upload(owner);
        var foreign =
                new RoomService.UpdateRoomCommand(
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        false,
                        new RoomService.ThemeCommand(
                                RoomTheme.Kind.UPLOADED_IMAGE, null, "image-dark-40", other),
                        true);
        assertThatThrownBy(() -> rooms.update(cohost, room.room().id(), revision + 1, foreign))
                .isInstanceOf(MediaException.class);
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    void unusedDeletionCannotRaceWithACommittedRoomAttachment() throws Exception {
        UserId owner = user();
        UUID id = upload(owner);
        var attached = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first =
                    executor.submit(
                            () ->
                                    new org.springframework.transaction.support.TransactionTemplate(
                                                    transactions)
                                            .execute(
                                                    status -> {
                                                        rooms.create(owner, command(id));
                                                        attached.countDown();
                                                        try {
                                                            release.await(
                                                                    10,
                                                                    java.util.concurrent.TimeUnit
                                                                            .SECONDS);
                                                        } catch (InterruptedException failure) {
                                                            Thread.currentThread().interrupt();
                                                        }
                                                        return true;
                                                    }));
            assertThat(attached.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var second =
                    executor.submit(
                            () -> {
                                try {
                                    media.delete(owner, id);
                                    return "deleted";
                                } catch (MediaException failure) {
                                    return failure.code();
                                }
                            });
            release.countDown();
            assertThat(first.get(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(15, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo("MEDIA_UNAVAILABLE");
            assertThat(objects.ready).containsKey(id);
        } finally {
            release.countDown();
        }
    }

    @Test
    void pendingPhotosAndWrongHashesNeverPublishOrReplaceTheme() throws Exception {
        UserId owner = user();
        byte[] bytes = photo();
        UUID id = authorization(owner, bytes).mediaAssetId();
        assertThatThrownBy(() -> rooms.create(owner, command(id)))
                .isInstanceOf(MediaException.class);
        objects.raw.put(id, new byte[bytes.length]);
        assertThatThrownBy(() -> media.complete(owner, id, hash(bytes)))
                .isInstanceOf(MediaException.class);
        assertThat(objects.ready).doesNotContainKey(id);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM app.media_asset WHERE id=?", String.class, id))
                .isEqualTo("REJECTED");
    }

    @Test
    void uncertainAndRejectedModerationNeverBecomeReadable() throws Exception {
        for (var result :
                List.of(PhotoModerator.Decision.UNCERTAIN, PhotoModerator.Decision.REJECTED)) {
            decision = result;
            UserId owner = user();
            byte[] bytes = photo();
            UUID id = authorization(owner, bytes).mediaAssetId();
            objects.raw.put(id, bytes);
            assertThat(media.complete(owner, id, hash(bytes)).status())
                    .isIn("MANUAL_REVIEW", "REJECTED");
            assertThatThrownBy(() -> media.delivery(owner, id)).isInstanceOf(MediaException.class);
            assertThatThrownBy(() -> rooms.create(owner, command(id)))
                    .isInstanceOf(MediaException.class);
            assertThat(objects.ready).doesNotContainKey(id);
        }
    }

    @Test
    void completionIsIdempotentAndUnusedDeletionIsProtected() throws Exception {
        UserId owner = user();
        UUID id = upload(owner);
        assertThat(media.complete(owner, id, hash(photo())).status()).isEqualTo("READY");
        assertThat(objects.publishes).isEqualTo(1);
        assertThatThrownBy(() -> media.delete(user(), id)).isInstanceOf(MediaException.class);
        media.delete(owner, id);
        assertThat(objects.ready).doesNotContainKey(id);
    }

    @Test
    void publishedCoverCannotBeDeletedWhileReferenced() throws Exception {
        UserId owner = user();
        UUID id = upload(owner);
        rooms.create(owner, command(id));
        assertThatThrownBy(() -> media.delete(owner, id)).isInstanceOf(MediaException.class);
        assertThat(objects.ready).containsKey(id);
    }

    @Test
    void accountPurgeFallsBackRoomThemeAndRetriesFailedStorageCleanup() throws Exception {
        UserId owner = user();
        UUID id = upload(owner);
        var room = rooms.create(owner, command(id));
        jdbc.update("UPDATE app.app_user SET status='deletion_pending' WHERE id=?", owner.value());
        assertThatThrownBy(() -> media.delivery(owner, id)).isInstanceOf(MediaException.class);
        objects.failDelete = true;
        assertThatThrownBy(() -> media.purgeUser(owner.value()))
                .isInstanceOf(MediaException.class)
                .hasMessageNotContaining("private-provider-error");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT kind FROM app.room_theme WHERE room_id=?",
                                String.class,
                                room.room().id().value()))
                .isEqualTo("preset");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM app.media_asset WHERE id=?", String.class, id))
                .isEqualTo("DELETING");
        objects.failDelete = false;
        media.purgeUser(owner.value());
        assertThat(objects.ready).doesNotContainKey(id);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM app.media_asset WHERE id=?", String.class, id))
                .isEqualTo("DELETING");
    }

    @Test
    void completedUploadsDoNotExposeOriginalBytesOrUrlsInDiagnostics() throws Exception {
        UserId owner = user();
        var auth = authorization(owner, photo());
        assertThat(auth.toString()).doesNotContain("private.test");
        assertThat(auth.upload().toString()).doesNotContain("private.test");
        assertThat(new MediaException(422, "MEDIA_HASH_MISMATCH").getMessage())
                .doesNotContain("hash", "private.test");
    }

    @Test
    void uploadRateLimitAndExpiredGrantsAreEnforced() throws Exception {
        UserId owner = user();
        var auth = authorization(owner, photo());
        jdbc.update(
                "UPDATE app.media_asset SET upload_expires_at=? WHERE id=?",
                java.sql.Timestamp.from(clock.instant().minusSeconds(1)),
                auth.mediaAssetId());
        assertThatThrownBy(() -> media.complete(owner, auth.mediaAssetId(), hash(photo())))
                .isInstanceOf(MediaException.class);
        jdbc.update(
                "INSERT INTO app.media_asset SELECT"
                    + " gen_random_uuid(),owner_user_id,content_type,size_bytes,width_px,height_px,"
                    + "content_sha256,status,claim_token,attempts,upload_expires_at,created_at,updated_at"
                    + " FROM app.media_asset CROSS JOIN generate_series(1,39) WHERE id=?",
                auth.mediaAssetId());
        assertThatThrownBy(() -> authorization(owner, photo())).isInstanceOf(MediaException.class);
    }
}
