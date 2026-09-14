package com.hyped.app.identity.application.model;

import java.util.Objects;

public record SessionTokenPair(IssuedAccessToken accessToken, GeneratedRefreshToken refreshToken) {

    public SessionTokenPair {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
    }
}
