package com.hyped.app.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException.Reason;
import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.domain.UserId;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class KmsFirestorePersonalDataProtectionAdapterTest {
    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");
    private static final String ENVIRONMENT = "test";
    private static final String KMS_KEY =
            "projects/test/locations/global/keyRings/personal-data/cryptoKeys/user-deks";
    private FakeKms kms;
    private FakeRegistry registry;
    private KmsFirestorePersonalDataProtectionAdapter adapter;

    @BeforeEach
    void setUp() {
        kms = new FakeKms();
        registry = new FakeRegistry();
        var properties = new PersonalDataProtectionProperties(true, ENVIRONMENT,
                new PersonalDataProtectionProperties.Kms(KMS_KEY),
                new PersonalDataProtectionProperties.Firestore("test-project", "key-registry", "user_keys"));
        adapter = new KmsFirestorePersonalDataProtectionAdapter(kms, registry,
                new AesGcmEnvelopeCipher(), properties, Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom());
    }

    @Test
    void createsOneRandomWrappedDekAndRetriesWithSameReference() {
        UserId userId = userId();
        String first = adapter.createKey(userId);
        String second = adapter.createKey(userId);

        assertThat(first).isEqualTo(second).matches("pdk1_[A-Za-z0-9_-]{32}");
        assertThat(kms.wrapCalls).hasValue(1);
        assertThat(kms.lastPlaintextLength).isEqualTo(32);
        PersonalDataKeyRecord stored = registry.find(userId).orElseThrow();
        assertThat(stored.keyReference()).isEqualTo(first);
        assertThat(stored.wrappedDek()).isNotNull().hasSize(32);
        assertThat(stored.toString()).doesNotContain(first, userId.toString(), KMS_KEY);
    }

    @Test
    void concurrentCreationSelectsExactlyOneUsableRecord() throws Exception {
        UserId userId = userId();
        try (var executor = Executors.newFixedThreadPool(12)) {
            List<Callable<String>> tasks = IntStream.range(0, 60)
                    .mapToObj(index -> (Callable<String>) () -> adapter.createKey(userId))
                    .toList();
            List<String> references = executor.invokeAll(tasks).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).toList();
            assertThat(references).containsOnly(references.getFirst());
        }
        assertThat(registry.records).hasSize(1);
        assertThat(registry.owners).hasSize(1);
        assertThat(registry.find(userId).orElseThrow().status()).isEqualTo(PersonalDataKeyStatus.ACTIVE);
    }

    @ParameterizedTest
    @EnumSource(PersonalDataField.class)
    void encryptsAndDecryptsWithFieldSpecificContext(PersonalDataField field) {
        UserId userId = userId();
        String reference = adapter.createKey(userId);
        byte[] plaintext = new byte[]{0, 1, -1, 127, -128};

        byte[] ciphertext = adapter.encrypt(userId, reference, field, plaintext);
        Arrays.fill(plaintext, (byte) 9);
        assertThat(adapter.decrypt(userId, reference, field, ciphertext))
                .containsExactly(0, 1, -1, 127, -128);
        PersonalDataField other = field == PersonalDataField.USER_IDENTITY_EMAIL
                ? PersonalDataField.USER_PROFILE_DISPLAY_NAME : PersonalDataField.USER_IDENTITY_EMAIL;
        assertFailure(() -> adapter.decrypt(userId, reference, other, ciphertext), Reason.AUTHENTICATION_FAILED);
    }

    @Test
    void rejectsWrongUserAndReference() {
        UserId owner = userId();
        UserId other = userId();
        String reference = adapter.createKey(owner);
        String unknown = "pdk1_" + "A".repeat(32);

        assertFailure(() -> adapter.encrypt(other, reference, PersonalDataField.REPORT_DETAILS, new byte[0]),
                Reason.KEY_UNAVAILABLE);
        assertFailure(() -> adapter.encrypt(owner, unknown, PersonalDataField.REPORT_DETAILS, new byte[0]),
                Reason.INVALID_INPUT);
        assertFailure(() -> adapter.destroyKey(other, reference), Reason.INVALID_INPUT);
        assertFailure(() -> adapter.encrypt(owner, "not-a-reference", PersonalDataField.REPORT_DETAILS, new byte[0]),
                Reason.INVALID_INPUT);
    }

    @Test
    void missingAndDestroyedKeysFailClosedWithoutRecreation() {
        UserId userId = userId();
        String unknown = "pdk1_" + "A".repeat(32);
        assertFailure(() -> adapter.decrypt(userId, unknown, PersonalDataField.USER_IDENTITY_EMAIL, new byte[29]),
                Reason.KEY_UNAVAILABLE);

        String reference = adapter.createKey(userId);
        byte[] ciphertext = adapter.encrypt(
                userId, reference, PersonalDataField.USER_PROFILE_DISPLAY_NAME, new byte[]{1});
        adapter.destroyKey(userId, reference);
        adapter.destroyKey(userId, reference);

        PersonalDataKeyRecord destroyed = registry.find(userId).orElseThrow();
        assertThat(destroyed.status()).isEqualTo(PersonalDataKeyStatus.DESTROYED);
        assertThat(destroyed.wrappedDek()).isNull();
        assertThat(destroyed.destroyedAt()).isEqualTo(NOW);
        assertFailure(() -> adapter.decrypt(
                userId, reference, PersonalDataField.USER_PROFILE_DISPLAY_NAME, ciphertext), Reason.KEY_UNAVAILABLE);
        assertFailure(() -> adapter.encrypt(
                userId, reference, PersonalDataField.USER_PROFILE_DISPLAY_NAME, new byte[]{2}),
                Reason.KEY_UNAVAILABLE);
        assertFailure(() -> adapter.createKey(userId), Reason.KEY_UNAVAILABLE);
        assertThat(kms.wrapCalls).hasValue(1);
    }

    @Test
    void missingKeyDestructionIsIdempotentCleanupPath() {
        UserId userId = userId();
        String unknown = "pdk1_" + "B".repeat(32);
        adapter.destroyKey(userId, unknown);
        adapter.destroyKey(userId, unknown);
        assertThat(registry.records).isEmpty();
    }

    @Test
    void mapsKmsAndRegistryFailuresWithoutLeakingProviderDetails() {
        UserId userId = userId();
        kms.failWrap = true;
        assertSafeProviderFailure(() -> adapter.createKey(userId));
        kms.failWrap = false;
        String reference = adapter.createKey(userId);
        kms.failUnwrap = true;
        assertSafeProviderFailure(() -> adapter.encrypt(
                userId, reference, PersonalDataField.DEVICE_FCM_TOKEN, new byte[]{1}));
        kms.failUnwrap = false;
        registry.fail = true;
        assertSafeProviderFailure(() -> adapter.destroyKey(userId, reference));
    }

    @Test
    void prohibitsInvocationInsideDatabaseTransactions() {
        Transactional annotation = KmsFirestorePersonalDataProtectionAdapter.class
                .getAnnotation(Transactional.class);
        assertThat(annotation.propagation()).isEqualTo(Propagation.NEVER);
    }

    private void assertSafeProviderFailure(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(
                PersonalDataProtectionException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(Reason.KEY_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo("KEY_UNAVAILABLE");
                    assertThat(exception.toString()).doesNotContain("provider-sensitive-value", KMS_KEY);
                });
    }

    private static void assertFailure(Runnable operation, Reason reason) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(
                PersonalDataProtectionException.class,
                exception -> assertThat(exception.reason()).isEqualTo(reason));
    }

    private static UserId userId() {
        return new UserId(UUID.randomUUID());
    }

    private static final class FakeKms implements PersonalDataKmsClient {
        private final AtomicInteger wrapCalls = new AtomicInteger();
        private volatile int lastPlaintextLength;
        private volatile boolean failWrap;
        private volatile boolean failUnwrap;

        @Override
        public byte[] wrap(String kmsKeyName, byte[] plaintextDek, byte[] associatedData) {
            if (failWrap) {
                throw new IllegalStateException("provider-sensitive-value");
            }
            wrapCalls.incrementAndGet();
            lastPlaintextLength = plaintextDek.length;
            return transform(plaintextDek);
        }

        @Override
        public byte[] unwrap(String kmsKeyName, byte[] wrappedDek, byte[] associatedData) {
            if (failUnwrap) {
                throw new IllegalStateException("provider-sensitive-value");
            }
            return transform(wrappedDek);
        }

        private static byte[] transform(byte[] input) {
            byte[] output = input.clone();
            for (int i = 0; i < output.length; i++) {
                output[i] ^= (byte) 0xA5;
            }
            return output;
        }
    }

    private static final class FakeRegistry implements PersonalDataKeyRegistryClient {
        private final ConcurrentHashMap<UserId, PersonalDataKeyRecord> records = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, UserId> owners = new ConcurrentHashMap<>();
        private volatile boolean fail;

        @Override
        public Optional<PersonalDataKeyRecord> find(UserId userId) {
            maybeFail();
            return Optional.ofNullable(records.get(userId));
        }

        @Override
        public Optional<UserId> findOwner(String keyReference) {
            maybeFail();
            return Optional.ofNullable(owners.get(keyReference));
        }

        @Override
        public PersonalDataKeyRecord createIfAbsent(PersonalDataKeyRecord candidate) {
            maybeFail();
            PersonalDataKeyRecord selected = records.putIfAbsent(candidate.userId(), candidate);
            if (selected == null) {
                owners.put(candidate.keyReference(), candidate.userId());
                return candidate;
            }
            return selected;
        }

        @Override
        public void destroy(UserId userId, String keyReference, Instant destroyedAt) {
            maybeFail();
            records.computeIfPresent(userId, (ignored, record) -> {
                if (!record.keyReference().equals(keyReference)) {
                    throw new PersonalDataProtectionException(Reason.INVALID_INPUT);
                }
                return record.status() == PersonalDataKeyStatus.DESTROYED
                        ? record : record.destroyed(destroyedAt);
            });
        }

        private void maybeFail() {
            if (fail) {
                throw new IllegalStateException("provider-sensitive-value");
            }
        }
    }
}
