package com.hyped.app.identity.application.port.out;

import com.hyped.app.identity.application.model.VerifiedProviderIdentity;

public interface IdentityTokenVerifier {

    VerifiedProviderIdentity verify(String firebaseIdToken);
}
