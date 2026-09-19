package com.hyped.app.identity.infrastructure.crypto;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Blob;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException.Reason;
import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

final class FirestorePersonalDataKeyRegistryClient implements PersonalDataKeyRegistryClient {
    private static final String FORMAT_VERSION = "1";
    private final Firestore firestore;
    private final String collection;

    FirestorePersonalDataKeyRegistryClient(Firestore firestore, String collection) {
        this.firestore = firestore;
        this.collection = collection;
    }

    @Override
    public Optional<PersonalDataKeyRecord> find(UserId userId) {
        DocumentSnapshot snapshot = await(document(userId).get());
        return snapshot.exists() ? Optional.of(toRecord(snapshot)) : Optional.empty();
    }

    @Override
    public Optional<UserId> findOwner(String keyReference) {
        DocumentSnapshot snapshot = await(referenceDocument(keyReference).get());
        if (!snapshot.exists()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new UserId(UUID.fromString(required(snapshot, "user_id"))));
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    @Override
    public PersonalDataKeyRecord createIfAbsent(PersonalDataKeyRecord candidate) {
        return await(firestore.runTransaction(transaction -> {
            DocumentReference document = document(candidate.userId());
            DocumentSnapshot snapshot = transaction.get(document).get();
            if (snapshot.exists()) {
                return toRecord(snapshot);
            }
            DocumentReference reference = referenceDocument(candidate.keyReference());
            DocumentSnapshot referenceSnapshot = transaction.get(reference).get();
            if (referenceSnapshot.exists()) {
                throw unavailable();
            }
            transaction.set(document, toMap(candidate));
            transaction.set(reference, Map.of("user_id", candidate.userId().toString()));
            return candidate;
        }));
    }

    @Override
    public void destroy(UserId userId, String keyReference, Instant destroyedAt) {
        await(firestore.runTransaction(transaction -> {
            DocumentReference document = document(userId);
            DocumentSnapshot snapshot = transaction.get(document).get();
            if (!snapshot.exists()) {
                return null;
            }
            PersonalDataKeyRecord existing = toRecord(snapshot);
            if (!existing.userId().equals(userId) || !existing.keyReference().equals(keyReference)) {
                throw new PersonalDataProtectionException(Reason.INVALID_INPUT);
            }
            if (existing.status() == PersonalDataKeyStatus.ACTIVE) {
                transaction.set(document, toMap(existing.destroyed(destroyedAt)));
            }
            return null;
        }));
    }

    private DocumentReference document(UserId userId) {
        return firestore.collection(collection).document(userId.toString());
    }

    private DocumentReference referenceDocument(String keyReference) {
        return firestore.collection(collection + "_references").document(keyReference);
    }

    private static Map<String, Object> toMap(PersonalDataKeyRecord record) {
        Map<String, Object> values = new HashMap<>();
        values.put("format_version", FORMAT_VERSION);
        values.put("user_id", record.userId().toString());
        values.put("key_reference", record.keyReference());
        values.put("environment", record.environment());
        values.put("kms_key_name", record.kmsKeyName());
        values.put("status", record.status().name().toLowerCase(java.util.Locale.ROOT));
        values.put("created_at", timestamp(record.createdAt()));
        if (record.wrappedDek() != null) {
            values.put("wrapped_dek", Blob.fromBytes(record.wrappedDek()));
        }
        if (record.destroyedAt() != null) {
            values.put("destroyed_at", timestamp(record.destroyedAt()));
        }
        return values;
    }

    private static PersonalDataKeyRecord toRecord(DocumentSnapshot snapshot) {
        try {
            if (!FORMAT_VERSION.equals(snapshot.getString("format_version"))) {
                throw unavailable();
            }
            UserId userId = new UserId(UUID.fromString(required(snapshot, "user_id")));
            PersonalDataKeyStatus status = PersonalDataKeyStatus.valueOf(
                    required(snapshot, "status").toUpperCase(java.util.Locale.ROOT));
            Blob wrapped = snapshot.getBlob("wrapped_dek");
            return new PersonalDataKeyRecord(userId, required(snapshot, "key_reference"),
                    required(snapshot, "environment"), required(snapshot, "kms_key_name"),
                    wrapped == null ? null : wrapped.toBytes(), status,
                    instant(snapshot, "created_at"), optionalInstant(snapshot, "destroyed_at"));
        } catch (PersonalDataProtectionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private static String required(DocumentSnapshot snapshot, String field) {
        String value = snapshot.getString(field);
        if (value == null) {
            throw unavailable();
        }
        return value;
    }

    private static Instant instant(DocumentSnapshot snapshot, String field) {
        Timestamp value = snapshot.getTimestamp(field);
        if (value == null) {
            throw unavailable();
        }
        return value.toSqlTimestamp().toInstant();
    }

    private static Instant optionalInstant(DocumentSnapshot snapshot, String field) {
        Timestamp value = snapshot.getTimestamp(field);
        return value == null ? null : value.toSqlTimestamp().toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.ofTimeSecondsAndNanos(value.getEpochSecond(), value.getNano());
    }

    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof PersonalDataProtectionException protectionException) {
                throw protectionException;
            }
            throw unavailable();
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private static PersonalDataProtectionException unavailable() {
        return new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
    }
}
