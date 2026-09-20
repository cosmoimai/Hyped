package com.hyped.app.identity.application.port.out;

import com.hyped.app.identity.domain.IdentityProvider;

public interface IdentityLookupProtector {
    byte[] protectProviderSubject(IdentityProvider provider, String providerSubject);

    /** Finds candidates only; callers must verify email ownership and never automatically link accounts. */
    byte[] protectVerifiedEmail(String email);
}
