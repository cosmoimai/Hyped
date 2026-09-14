package com.hyped.app.identity.application.port.out;

public interface RefreshTokenDigester {

    /** Returns a fresh 32-byte SHA-256 digest of the encoded high-entropy refresh token. */
    byte[] digest(String refreshToken);
}
