package com.hyped.app.identity.application.port.out;

import com.hyped.app.identity.application.model.IssuedAccessToken;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserId;

public interface AccessTokenIssuer {

    /** The did claim identifies the app installation, not physical hardware. */
    IssuedAccessToken issue(UserId userId, SessionId sessionId, InstallationId installationId);
}
