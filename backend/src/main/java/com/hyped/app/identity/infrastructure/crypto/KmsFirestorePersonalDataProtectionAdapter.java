package com.hyped.app.identity.infrastructure.crypto;

import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException.Reason;
import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.application.model.ProvisionedPersonalDataKey;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.application.port.out.PersonalDataKeyManager;
import com.hyped.app.identity.domain.UserId;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.NEVER)
final class KmsFirestorePersonalDataProtectionAdapter implements PersonalDataKeyManager, PersonalDataCipher {
    private static final int DEK_LENGTH = 32;
    private static final int REFERENCE_RANDOM_LENGTH = 24;
    private final PersonalDataKmsClient kms;
    private final PersonalDataKeyRegistryClient registry;
    private final AesGcmEnvelopeCipher cipher;
    private final PersonalDataProtectionProperties properties;
    private final Clock clock;
    private final SecureRandom random;

    KmsFirestorePersonalDataProtectionAdapter(
            PersonalDataKmsClient kms,
            PersonalDataKeyRegistryClient registry,
            AesGcmEnvelopeCipher cipher,
            PersonalDataProtectionProperties properties,
            Clock clock,
            SecureRandom random) {
        this.kms = kms;
        this.registry = registry;
        this.cipher = cipher;
        this.properties = properties;
        this.clock = clock;
        this.random = random;
    }

    @Override
    public ProvisionedPersonalDataKey provisionKey(UserId userId) {
        Objects.requireNonNull(userId, "userId");
        Optional<PersonalDataKeyRecord> existing = providerCall(() -> registry.find(userId));
        if (existing.isPresent()) {
            PersonalDataKeyRecord record = existing.orElseThrow();
            String reference = requireUsable(record, userId, record.keyReference()).keyReference();
            return new ProvisionedPersonalDataKey(reference, false);
        }

        byte[] plaintextDek = new byte[DEK_LENGTH];
        byte[] wrappingContext = wrappingContext(userId);
        byte[] wrappedDek = null;
        random.nextBytes(plaintextDek);
        try {
            wrappedDek = providerCall(() -> kms.wrap(properties.kms().keyName(), plaintextDek, wrappingContext));
            if (wrappedDek == null || wrappedDek.length == 0) {
                throw unavailable();
            }
            PersonalDataKeyRecord candidate = new PersonalDataKeyRecord(
                    userId, newReference(), properties.environment(), properties.kms().keyName(), wrappedDek,
                    PersonalDataKeyStatus.ACTIVE, clock.instant(), null);
            PersonalDataKeyRecord selected = providerCall(() -> registry.createIfAbsent(candidate));
            String reference = requireUsable(selected, userId, selected.keyReference()).keyReference();
            return new ProvisionedPersonalDataKey(reference, reference.equals(candidate.keyReference()));
        } finally {
            Arrays.fill(plaintextDek, (byte) 0);
            Arrays.fill(wrappingContext, (byte) 0);
            if (wrappedDek != null) {
                Arrays.fill(wrappedDek, (byte) 0);
            }
        }
    }

    @Override
    public void destroyKey(UserId userId, String keyReference) {
        Objects.requireNonNull(userId, "userId");
        validateReference(keyReference);
        Optional<UserId> owner = providerCall(() -> registry.findOwner(keyReference));
        if (owner.isPresent() && !owner.orElseThrow().equals(userId)) {
            throw invalidInput();
        }
        providerCall(() -> {
            registry.destroy(userId, keyReference, clock.instant());
            return null;
        });
    }

    @Override
    public byte[] encrypt(UserId userId, String keyReference, PersonalDataField field, byte[] plaintext) {
        if (plaintext == null) {
            throw invalidInput();
        }
        return withKey(userId, keyReference, field,
                key -> cipher.encrypt(key, plaintext, encryptionContext(userId, field)));
    }

    @Override
    public byte[] decrypt(UserId userId, String keyReference, PersonalDataField field, byte[] ciphertext) {
        if (ciphertext == null) {
            throw invalidInput();
        }
        return withKey(userId, keyReference, field,
                key -> cipher.decrypt(key, ciphertext, encryptionContext(userId, field)));
    }

    private byte[] withKey(UserId userId, String keyReference, PersonalDataField field, KeyOperation operation) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(field, "field");
        validateReference(keyReference);
        Optional<PersonalDataKeyRecord> stored = providerCall(() -> registry.find(userId));
        PersonalDataKeyRecord record = requireUsable(
                stored.orElseThrow(KmsFirestorePersonalDataProtectionAdapter::unavailable), userId, keyReference);
        byte[] wrappingContext = wrappingContext(userId);
        byte[] plaintextDek = null;
        EphemeralAesKey key = null;
        try {
            plaintextDek = providerCall(
                    () -> kms.unwrap(record.kmsKeyName(), record.wrappedDek(), wrappingContext));
            if (plaintextDek == null || plaintextDek.length != DEK_LENGTH) {
                throw unavailable();
            }
            key = new EphemeralAesKey(plaintextDek);
            return operation.apply(key);
        } finally {
            Arrays.fill(wrappingContext, (byte) 0);
            if (plaintextDek != null) {
                Arrays.fill(plaintextDek, (byte) 0);
            }
            destroy(key);
        }
    }

    private PersonalDataKeyRecord requireUsable(
            PersonalDataKeyRecord record, UserId userId, String keyReference) {
        if (!record.userId().equals(userId) || !record.keyReference().equals(keyReference)) {
            throw invalidInput();
        }
        if (record.status() != PersonalDataKeyStatus.ACTIVE
                || !record.environment().equals(properties.environment())
                || !record.kmsKeyName().equals(properties.kms().keyName())) {
            throw unavailable();
        }
        return record;
    }

    private EncryptionContext encryptionContext(UserId userId, PersonalDataField field) {
        FieldLocation location = FieldLocation.from(field);
        return new EncryptionContext(properties.environment(), userId, location.table(), location.column());
    }

    private byte[] wrappingContext(UserId userId) {
        return CryptoEncoding.encode("hyped.personal-data-dek.v1", properties.environment(), userId.toString());
    }

    private String newReference() {
        byte[] randomBytes = new byte[REFERENCE_RANDOM_LENGTH];
        random.nextBytes(randomBytes);
        try {
            return "pdk1_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        } finally {
            Arrays.fill(randomBytes, (byte) 0);
        }
    }

    private static void validateReference(String keyReference) {
        if (keyReference == null || !keyReference.matches("pdk1_[A-Za-z0-9_-]{32}")) {
            throw invalidInput();
        }
    }

    private static <T> T providerCall(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (PersonalDataProtectionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private static void destroy(EphemeralAesKey key) {
        if (key != null) {
            try {
                key.destroy();
            } catch (javax.security.auth.DestroyFailedException exception) {
                throw unavailable();
            }
        }
    }

    private static PersonalDataProtectionException invalidInput() {
        return new PersonalDataProtectionException(Reason.INVALID_INPUT);
    }

    private static PersonalDataProtectionException unavailable() {
        return new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
    }

    @FunctionalInterface
    private interface KeyOperation {
        byte[] apply(EphemeralAesKey key);
    }

    private record FieldLocation(String table, String column) {
        private static FieldLocation from(PersonalDataField field) {
            return switch (field) {
                case USER_IDENTITY_EMAIL -> new FieldLocation("app.user_identity", "email_ciphertext");
                case USER_PROFILE_DISPLAY_NAME ->
                        new FieldLocation("app.user_profile", "display_name_ciphertext");
                case USER_PROFILE_PROVIDER_PHOTO_URL ->
                        new FieldLocation("app.user_profile", "provider_photo_url_ciphertext");
                case DEVICE_FCM_TOKEN -> new FieldLocation("app.device_registration", "fcm_token_ciphertext");
                case REPORT_DETAILS -> new FieldLocation("app.report", "details_ciphertext");
            };
        }
    }
}
