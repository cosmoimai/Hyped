package com.hyped.app.invitation.infrastructure;

import com.hyped.app.invitation.application.port.out.InvitationCryptography;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class InvitationCipher implements InvitationCryptography {
    public static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final InvitationProperties properties;
    private final SecureRandom random;
    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec lookupKey;

    public InvitationCipher(InvitationProperties properties, SecureRandom random) {
        this.properties = properties;
        this.random = random;
        this.encryptionKey = new SecretKeySpec(Base64.getDecoder().decode(properties.encryptionKey()), "AES");
        this.lookupKey = new SecretKeySpec(Base64.getDecoder().decode(properties.lookupHmacKey()), "HmacSHA256");
    }

    @Override
    public String newCode() {
        StringBuilder code = new StringBuilder(8);
        for (int index = 0; index < 8; index++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    @Override
    public String newToken() {
        byte[] token = new byte[32];
        random.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    @Override
    public byte[] digest(String purpose, String value) {
        try {
            if (purpose.equals("link")) {
                return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(lookupKey);
            return mac.doFinal((properties.environment() + ":" + purpose + ":" + value)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Invitation cryptography unavailable");
        }
    }

    @Override
    public byte[] encrypt(String context, String value) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, context, nonce);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + nonce.length + encrypted.length)
                    .put((byte) 1).put(nonce).put(encrypted).array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Invitation encryption unavailable");
        }
    }

    @Override
    public String decrypt(String context, byte[] ciphertext) {
        try {
            if (ciphertext.length < 29 || ciphertext[0] != 1) {
                throw new GeneralSecurityException();
            }
            ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
            buffer.get();
            byte[] nonce = new byte[12];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            return new String(cipher(Cipher.DECRYPT_MODE, context, nonce).doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Invalid protected invitation value");
        }
    }

    @Override
    public String inviteUrl(String token) {
        return properties.publicBaseUrl() + "/invite/" + token;
    }

    private Cipher cipher(int mode, String context, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, encryptionKey, new GCMParameterSpec(128, nonce));
        cipher.updateAAD((properties.environment() + ":invitation:v1:" + context).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
