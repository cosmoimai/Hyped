package com.hyped.app.identity.application.model;

import com.hyped.app.identity.domain.UserId;
import java.util.Objects;
import java.util.UUID;

public record AuthenticationUserProfile(
        UserId id,
        String displayName,
        UUID photoMediaId,
        String providerPhotoUrl,
        long profileRevision) {

    public AuthenticationUserProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank() || profileRevision <= 0) {
            throw new IllegalArgumentException("Authentication profile is invalid");
        }
    }
}
