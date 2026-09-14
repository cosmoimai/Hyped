package com.hyped.app.identity.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

final class TestTokenKeys {

    static final KeyPair PAIR = generate();
    static final Resource PUBLIC_KEY = pem("PUBLIC KEY", PAIR.getPublic().getEncoded());
    static final Resource PRIVATE_KEY = pem("PRIVATE KEY", PAIR.getPrivate().getEncoded());

    private TestTokenKeys() {}

    static TokenProperties properties() {
        return new TokenProperties(true, "https://issuer.example.test", "hyped-mobile", "test-key-1",
                PUBLIC_KEY, PRIVATE_KEY, Duration.ofHours(1), Duration.ofDays(30));
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Resource pem(String type, byte[] encoded) {
        String value = "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
        return new ByteArrayResource(value.getBytes(StandardCharsets.US_ASCII));
    }
}
