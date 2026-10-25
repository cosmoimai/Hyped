package com.hyped.app.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.domain.UserId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LocalPersonalDataStoreTest {
    @TempDir
    Path directory;

    @BeforeEach
    void prepare() throws Exception {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        Files.write(directory.resolve("wrapping-key.bin"), bytes);
        Files.setPosixFilePermissions(directory.resolve("wrapping-key.bin"),
                PosixFilePermissions.fromString("rw-------"));
    }

    private KmsFirestorePersonalDataProtectionAdapter adapter(LocalPersonalDataStore store) {
        return new LocalPersonalDataProtectionConfiguration()
                .localPersonalDataProtectionAdapter(store, Clock.systemUTC());
    }

    @Test
    void encryptedUserDataSurvivesRestartAndRetainsFieldAndAccountBinding() throws Exception {
        UserId user = new UserId(UUID.randomUUID());
        String reference;
        byte[] encrypted;
        PersonalDataField field = PersonalDataField.values()[0];
        try (var store = new LocalPersonalDataStore(directory)) {
            var adapter = adapter(store);
            reference = adapter.createKey(user);
            encrypted = adapter.encrypt(user, reference, field, "private profile".getBytes());
            assertThat(encrypted).isNotEqualTo("private profile".getBytes());
        }
        try (var store = new LocalPersonalDataStore(directory)) {
            var adapter = adapter(store);
            assertThat(adapter.decrypt(user, reference, field, encrypted)).isEqualTo("private profile".getBytes());
            assertThatThrownBy(() -> adapter.decrypt(user, reference, PersonalDataField.values()[1], encrypted))
                    .isInstanceOf(PersonalDataProtectionException.class);
            assertThatThrownBy(() -> adapter.decrypt(new UserId(UUID.randomUUID()), reference, field, encrypted))
                    .isInstanceOf(PersonalDataProtectionException.class);
        }
    }

    @Test
    void concurrentCreationSelectsOneRecord() throws Exception {
        UserId user = new UserId(UUID.randomUUID());
        try (var store = new LocalPersonalDataStore(directory); var executor = Executors.newFixedThreadPool(8)) {
            var adapter = adapter(store);
            List<Callable<String>> work = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                work.add(() -> adapter.createKey(user));
            }
            var results = executor.invokeAll(work);
            String reference = results.getFirst().get();
            for (var result : results) {
                assertThat(result.get()).isEqualTo(reference);
            }
        }
    }

    @Test
    void destroyedKeyCannotBeRecreatedOrUsedAfterRestart() throws Exception {
        UserId user = new UserId(UUID.randomUUID());
        String reference;
        try (var store = new LocalPersonalDataStore(directory)) {
            var adapter = adapter(store);
            reference = adapter.createKey(user);
            adapter.destroyKey(user, reference);
            adapter.destroyKey(user, reference);
        }
        try (var store = new LocalPersonalDataStore(directory)) {
            assertThat(store.findOwner(reference)).contains(user);
            assertThat(store.find(user).orElseThrow().wrappedDek()).isNull();
            assertThatThrownBy(() -> adapter(store).createKey(user))
                    .isInstanceOf(PersonalDataProtectionException.class);
        }
    }

    @Test
    void wrappingRejectsWrongAssociatedDataAndTampering() throws Exception {
        try (var store = new LocalPersonalDataStore(directory)) {
            byte[] wrapped = store.wrap("local", new byte[32], new byte[] {1});
            assertThatThrownBy(() -> store.unwrap("local", wrapped, new byte[] {2}))
                    .isInstanceOf(PersonalDataProtectionException.class);
            wrapped[20] ^= 1;
            assertThatThrownBy(() -> store.unwrap("local", wrapped, new byte[] {1}))
                    .isInstanceOf(PersonalDataProtectionException.class);
        }
    }

    @Test
    void rejectsConcurrentProcessesAndReleasesLockOnClose() throws Exception {
        try (var store = new LocalPersonalDataStore(directory)) {
            assertThatThrownBy(() -> new LocalPersonalDataStore(directory))
                    .isInstanceOf(PersonalDataProtectionException.class);
        }
        try (var store = new LocalPersonalDataStore(directory)) {
            assertThat(store.find(new UserId(UUID.randomUUID()))).isEmpty();
        }
    }

    @Test
    void rejectsCorruptRecordsWithoutReturningFileContents() throws Exception {
        UserId user = new UserId(UUID.randomUUID());
        Path record = directory.resolve(user + ".key");
        Files.writeString(record, "secret-corrupt-content");
        Files.setPosixFilePermissions(record, PosixFilePermissions.fromString("rw-------"));
        try (var store = new LocalPersonalDataStore(directory)) {
            assertThatThrownBy(() -> store.find(user)).isInstanceOf(PersonalDataProtectionException.class)
                    .hasMessageNotContaining("secret-corrupt-content").hasMessageNotContaining(directory.toString());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsInsecurePermissions(boolean folder) throws Exception {
        Path target = folder ? directory : directory.resolve("wrapping-key.bin");
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString(folder ? "rwxr-xr-x" : "rw-r--r--"));
        assertThatThrownBy(() -> new LocalPersonalDataStore(directory))
                .isInstanceOf(PersonalDataProtectionException.class);
    }

    @Test
    void rejectsMissingOrSymlinkedWrappingKey() throws Exception {
        Path key = directory.resolve("wrapping-key.bin");
        Files.move(key, directory.resolve("original.bin"));
        assertThatThrownBy(() -> new LocalPersonalDataStore(directory))
                .isInstanceOf(PersonalDataProtectionException.class);
        Files.createSymbolicLink(key, directory.resolve("original.bin"));
        assertThatThrownBy(() -> new LocalPersonalDataStore(directory))
                .isInstanceOf(PersonalDataProtectionException.class);
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withUserConfiguration(LocalPersonalDataProtectionConfiguration.class,
                        PersonalDataProtectionConfiguration.class)
                .withBean(Clock.class, Clock::systemUTC)
                .withPropertyValues("hyped.personal-data.provider=local", "hyped.personal-data.enabled=true",
                        "hyped.personal-data.local-directory=" + directory,
                        "spring.profiles.active=local", "server.address=127.0.0.1");
    }

    @Test
    void explicitLocalConfigurationUsesNoCloudClients() {
        context().run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(LocalPersonalDataStore.class);
            assertThat(ctx).hasSingleBean(KmsFirestorePersonalDataProtectionAdapter.class);
            assertThat(ctx).doesNotHaveBean(com.google.cloud.firestore.Firestore.class);
            assertThat(ctx).doesNotHaveBean(com.google.cloud.kms.v1.KeyManagementServiceClient.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.profiles.active=default", "spring.profiles.active=local,production",
            "spring.profiles.active=local,staging", "server.address=0.0.0.0", "hyped.personal-data.enabled=false"})
    void localProviderFailsClosedOutsideExplicitLoopbackDevelopment(String setting) {
        context().withPropertyValues(setting).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void localStoreIsNotSelectedByDefault() {
        new ApplicationContextRunner().withUserConfiguration(LocalPersonalDataProtectionConfiguration.class)
                .run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(LocalPersonalDataStore.class));
    }
}
