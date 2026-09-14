package com.hyped.app.identity.infrastructure.security;

import com.hyped.app.identity.application.port.out.RefreshTokenDigester;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

final class Sha256RefreshTokenDigester implements RefreshTokenDigester {

    @Override
    public byte[] digest(String refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken");
        if (refreshToken.isBlank()) {
            throw new IllegalArgumentException("A refresh token is required");
        }
        byte[] bytes = refreshToken.getBytes(StandardCharsets.UTF_8);
        try {
            // A new MessageDigest avoids shared mutable state and returns a fresh byte array on every call.
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
