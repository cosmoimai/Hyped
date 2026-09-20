package com.hyped.app.identity.infrastructure.firebase;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("hyped.firebase")
public record FirebaseIdentityProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String projectId,
        @DefaultValue("60s") Duration clockSkew) {

    public FirebaseIdentityProperties {
        Objects.requireNonNull(clockSkew, "clockSkew");
        if (clockSkew.isNegative() || clockSkew.compareTo(Duration.ofMinutes(5)) > 0 || clockSkew.getNano() != 0) {
            throw new IllegalArgumentException("Firebase clock skew must be between 0 and 300 whole seconds");
        }
        if (enabled && (projectId == null || projectId.isBlank() || !projectId.equals(projectId.strip()))) {
            throw new IllegalArgumentException("Enabled Firebase verification requires an explicit project ID");
        }
    }
}
