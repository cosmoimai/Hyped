package com.hyped.app.identity.application.port.out;

import com.hyped.app.identity.application.model.GeneratedRefreshToken;

public interface RefreshTokenGenerator {

    /** Returns a request-local secret for delivery to the client; never log or persist its value. */
    GeneratedRefreshToken generate();
}
