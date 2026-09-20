package com.hyped.app.identity.infrastructure.crypto;

import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException.Reason;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Internal primitive only; future adapters own external key resolution and per-key usage limits. */
final class AesGcmEnvelopeCipher {
    private static final byte VERSION = 1;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom random = new SecureRandom();

    byte[] encrypt(SecretKey key, byte[] plaintext, EncryptionContext context) {
        requireInput(plaintext, context);
        byte[] copy = plaintext.clone();
        byte[] nonce = new byte[NONCE_LENGTH];
        random.nextBytes(nonce);
        try {
            byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, key, copy, nonce, context);
            return ByteBuffer.allocate(1 + NONCE_LENGTH + encrypted.length)
                    .put(VERSION).put(nonce).put(encrypted).array();
        } finally {
            Arrays.fill(copy, (byte) 0);
        }
    }

    byte[] decrypt(SecretKey key, byte[] envelope, EncryptionContext context) {
        requireInput(envelope, context);
        byte[] copy = envelope.clone();
        try {
            if (copy.length < 1 + NONCE_LENGTH + TAG_BITS / Byte.SIZE) {
                throw new PersonalDataProtectionException(Reason.MALFORMED_ENVELOPE);
            }
            if (copy[0] != VERSION) {
                throw new PersonalDataProtectionException(Reason.UNSUPPORTED_VERSION);
            }
            byte[] nonce = Arrays.copyOfRange(copy, 1, 1 + NONCE_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(copy, 1 + NONCE_LENGTH, copy.length);
            return crypt(Cipher.DECRYPT_MODE, key, encrypted, nonce, context);
        } finally {
            Arrays.fill(copy, (byte) 0);
        }
    }

    private byte[] crypt(int mode, SecretKey key, byte[] input, byte[] nonce, EncryptionContext context) {
        if (key == null || !"AES".equals(key.getAlgorithm())) {
            throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
        }
        byte[] encoded = key.getEncoded();
        if (encoded == null) {
            throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
        }
        byte[] keyCopy = encoded.clone();
        try {
            if (keyCopy.length != 32) {
                throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(keyCopy, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(context.associatedData(VERSION));
            return cipher.doFinal(input);
        } catch (AEADBadTagException exception) {
            throw new PersonalDataProtectionException(Reason.AUTHENTICATION_FAILED);
        } catch (GeneralSecurityException exception) {
            throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
        } finally {
            Arrays.fill(keyCopy, (byte) 0);
        }
    }

    private static void requireInput(byte[] input, EncryptionContext context) {
        if (input == null || context == null) {
            throw new PersonalDataProtectionException(Reason.INVALID_INPUT);
        }
    }
}
