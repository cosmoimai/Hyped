package com.hyped.app.identity.application.model;

import java.util.Objects;

public sealed interface RefreshSessionResult {

    record Success(SessionTokenPair tokens) implements RefreshSessionResult {
        public Success {
            Objects.requireNonNull(tokens, "tokens");
        }
    }

    record ReauthenticationRequired(String code) implements RefreshSessionResult {
        public ReauthenticationRequired {
            Objects.requireNonNull(code, "code");
        }
    }
}
