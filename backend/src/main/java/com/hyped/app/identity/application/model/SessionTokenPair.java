package com.hyped.app.identity.application.model;

import java.util.Objects;
import com.hyped.app.identity.domain.SessionId;

public record SessionTokenPair(
        SessionId sessionId,
        IssuedAccessToken accessToken,
        GeneratedRefreshToken refreshToken) {

    public SessionTokenPair {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
    }
}
