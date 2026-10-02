package com.hyped.app.invitation.infrastructure;

import java.net.URI;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("hyped.invitation")
public record InvitationProperties(
        @DefaultValue("false") boolean enabled,
        URI publicBaseUrl,
        String encryptionKey,
        String lookupHmacKey,
        @DefaultValue("production") String environment) {
    public InvitationProperties {
        if (enabled) {
            if (publicBaseUrl == null || !"https".equals(publicBaseUrl.getScheme())
                    || publicBaseUrl.getHost() == null || publicBaseUrl.getUserInfo() != null
                    || publicBaseUrl.getQuery() != null || publicBaseUrl.getFragment() != null
                    || publicBaseUrl.toString().endsWith("/")) {
                throw new IllegalArgumentException("Invitation public base URL must be HTTPS without a trailing slash");
            }
            requireKey(encryptionKey);
            requireKey(lookupHmacKey);
            if (encryptionKey.equals(lookupHmacKey) || environment == null || environment.isBlank()) {
                throw new IllegalArgumentException("Invitation keys must be independent and environment must be set");
            }
        }
    }

    private static void requireKey(String key) {
        try {
            if (key == null || Base64.getDecoder().decode(key).length != 32) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invitation keys must be base64-encoded 256-bit keys");
        }
    }

    @Override
    public String toString() {
        return "InvitationProperties[REDACTED]";
    }
}
