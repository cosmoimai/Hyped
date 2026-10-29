package com.hyped.app.identity.infrastructure.crypto;

import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException.Reason;
import com.hyped.app.identity.domain.UserId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Durable developer key registry outside PostgreSQL; not a production KMS or secure-erasure claim. */
final class LocalPersonalDataStore implements PersonalDataKmsClient, PersonalDataKeyRegistryClient, AutoCloseable {
    private final Path directory;
    private final byte[] master;
    private final FileChannel channel;
    private final FileLock lock;

    LocalPersonalDataStore(Path requested) {
        FileChannel opened = null;
        try {
            if (!requested.isAbsolute() || Files.isSymbolicLink(requested)) {
                throw unavailable();
            }
            directory = requested.toRealPath();
            for (Path parent = directory; parent != null; parent = parent.getParent()) {
                if (Files.exists(parent.resolve(".git"))) {
                    throw unavailable();
                }
            }
            privatePath(directory, "rwx------");
            Path masterPath = directory.resolve("wrapping-key.bin");
            privatePath(masterPath, "rw-------");
            if (Files.size(masterPath) != 32) {
                throw unavailable();
            }
            opened = FileChannel.open(directory.resolve("store.lock"), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            Files.setPosixFilePermissions(directory.resolve("store.lock"),
                    PosixFilePermissions.fromString("rw-------"));
            FileLock acquired = opened.tryLock();
            if (acquired == null) {
                throw unavailable();
            }
            master = Files.readAllBytes(masterPath);
            channel = opened;
            lock = acquired;
        } catch (IOException | RuntimeException failure) {
            if (opened != null) {
                try {
                    opened.close();
                } catch (IOException ignored) {
                    // The original sanitized failure remains authoritative.
                }
            }
            throw unavailable();
        }
    }

    @Override
    public synchronized byte[] wrap(String name, byte[] plaintext, byte[] associatedData) {
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, plaintext, nonce, associatedData);
        return ByteBuffer.allocate(1 + nonce.length + encrypted.length).put((byte) 1).put(nonce).put(encrypted).array();
    }

    @Override
    public synchronized byte[] unwrap(String name, byte[] wrapped, byte[] associatedData) {
        if (wrapped == null || wrapped.length < 29 || wrapped[0] != 1) {
            throw unavailable();
        }
        return crypt(Cipher.DECRYPT_MODE, Arrays.copyOfRange(wrapped, 13, wrapped.length),
                Arrays.copyOfRange(wrapped, 1, 13), associatedData);
    }

    private byte[] crypt(int mode, byte[] input, byte[] nonce, byte[] associatedData) {
        try {
            if (!lock.isValid()) {
                throw unavailable();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(master, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(associatedData);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException | RuntimeException failure) {
            throw unavailable();
        }
    }

    @Override
    public synchronized Optional<PersonalDataKeyRecord> find(UserId userId) {
        Path path = directory.resolve(userId + ".key");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            privatePath(path, "rw-------");
            if (Files.size(path) > 8192) {
                throw unavailable();
            }
            try (var input = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(path)))) {
                if (input.readInt() != 1) {
                    throw unavailable();
                }
                String reference = input.readUTF();
                String environment = input.readUTF();
                String keyName = input.readUTF();
                Instant created = Instant.parse(input.readUTF());
                boolean active = input.readBoolean();
                byte[] wrapped = active ? input.readNBytes(input.readUnsignedShort()) : null;
                Instant destroyed = active ? null : Instant.parse(input.readUTF());
                if (input.available() != 0 || (active && (wrapped == null || wrapped.length != 61))) {
                    throw unavailable();
                }
                return Optional.of(new PersonalDataKeyRecord(userId, reference, environment,
                        keyName, wrapped, active ? PersonalDataKeyStatus.ACTIVE : PersonalDataKeyStatus.DESTROYED,
                        created, destroyed));
            }
        } catch (IOException | RuntimeException failure) {
            throw unavailable();
        }
    }

    @Override
    public synchronized Optional<UserId> findOwner(String reference) {
        try (var files = Files.newDirectoryStream(directory, "*.key")) {
            for (Path path : files) {
                String filename = path.getFileName().toString();
                UserId user = new UserId(UUID.fromString(filename.substring(0, filename.length() - 4)));
                if (find(user).orElseThrow().keyReference().equals(reference)) {
                    return Optional.of(user);
                }
            }
            return Optional.empty();
        } catch (IOException | RuntimeException failure) {
            throw unavailable();
        }
    }

    @Override
    public synchronized PersonalDataKeyRecord createIfAbsent(PersonalDataKeyRecord candidate) {
        Optional<PersonalDataKeyRecord> existing = find(candidate.userId());
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        if (findOwner(candidate.keyReference()).isPresent()) {
            throw unavailable();
        }
        write(candidate);
        return candidate;
    }

    @Override
    public synchronized void destroy(UserId userId, String reference, Instant destroyedAt) {
        Optional<PersonalDataKeyRecord> found = find(userId);
        if (found.isEmpty()) {
            return;
        }
        PersonalDataKeyRecord existing = found.orElseThrow();
        if (!existing.keyReference().equals(reference)) {
            throw unavailable();
        }
        if (existing.status() == PersonalDataKeyStatus.ACTIVE) {
            write(existing.destroyed(destroyedAt));
        }
    }

    private void write(PersonalDataKeyRecord record) {
        Path temporary = null;
        try {
            if (!lock.isValid()) {
                throw unavailable();
            }
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeInt(1);
                output.writeUTF(record.keyReference());
                output.writeUTF(record.environment());
                output.writeUTF(record.kmsKeyName());
                output.writeUTF(record.createdAt().toString());
                boolean active = record.status() == PersonalDataKeyStatus.ACTIVE;
                output.writeBoolean(active);
                if (active) {
                    byte[] wrapped = record.wrappedDek();
                    output.writeShort(wrapped.length);
                    output.write(wrapped);
                } else {
                    output.writeUTF(record.destroyedAt().toString());
                }
            }
            temporary = Files.createTempFile(directory, "key-", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try (var file = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes.toByteArray());
                while (buffer.hasRemaining()) {
                    file.write(buffer);
                }
                file.force(true);
            }
            Files.move(temporary, directory.resolve(record.userId() + ".key"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException failure) {
            throw unavailable();
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // An incomplete temporary record is never used as an active key.
                }
            }
        }
    }

    private static void privatePath(Path path, String mode) throws IOException {
        if (Files.isSymbolicLink(path)
                || !Files.getPosixFilePermissions(path).equals(PosixFilePermissions.fromString(mode))) {
            throw unavailable();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        Arrays.fill(master, (byte) 0);
        lock.release();
        channel.close();
    }

    private static PersonalDataProtectionException unavailable() {
        return new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
    }
}
