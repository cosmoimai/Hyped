package com.hyped.app.invitation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class InvitationCipherTest {
    @Test
    void codesUseTheDocumentedAlphabetAndTokensContain256RandomBits() {
        var cipher = cipher();
        var codes = new HashSet<String>();
        for (int index = 0; index < 10_000; index++) {
            String code = cipher.newCode();
            assertThat(code).matches("[A-HJ-NP-Z2-9]{8}");
            codes.add(code);
        }
        assertThat(codes).hasSize(10_000);
        assertThat(Base64.getUrlDecoder().decode(cipher.newToken())).hasSize(32);
    }

    @Test
    void ciphertextIsRandomizedAuthenticatedAndBoundToItsContext() {
        var cipher = cipher();
        byte[] encrypted = cipher.encrypt("room:1:code", "ABCDEFGH");
        assertThat(cipher.encrypt("room:1:code", "ABCDEFGH")).isNotEqualTo(encrypted);
        assertThat(cipher.decrypt("room:1:code", encrypted)).isEqualTo("ABCDEFGH");
        assertThatThrownBy(() -> cipher.decrypt("other-room:1:code", encrypted))
                .isInstanceOf(IllegalArgumentException.class);
        encrypted[encrypted.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt("room:1:code", encrypted))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lookupHasDomainSeparationAndLinksHaveOnlyAnOpaquePathCredential() {
        var cipher = cipher();
        assertThat(cipher.digest("code", "ABCDEFGH")).hasSize(32)
                .isNotEqualTo(cipher.digest("request", "ABCDEFGH"));
        URI url = URI.create(cipher.inviteUrl(cipher.newToken()));
        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(url.getHost()).isEqualTo("invites.example.test");
        assertThat(url.getQuery()).isNull();
        assertThat(url.getFragment()).isNull();
        assertThat(url.getPath()).matches("/invite/[A-Za-z0-9_-]{43}");
    }

    @Test
    void configurationFailsClosedForInvalidUrlsAndKeys() {
        for (String url : new String[] {"http://example.test", "https://u:p@example.test", "https://example.test?q=x",
                "https://example.test#x", "https://example.test/"}) {
            assertThatThrownBy(() -> properties(url, key(1), key(2)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> properties("https://example.test", "short", key(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties("https://example.test", key(1), key(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private InvitationCipher cipher() {
        return new InvitationCipher(properties("https://invites.example.test", key(1), key(2)), new SecureRandom());
    }

    private InvitationProperties properties(String url, String encryption, String lookup) {
        return new InvitationProperties(true, URI.create(url), encryption, lookup, "test");
    }

    static String key(int value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
