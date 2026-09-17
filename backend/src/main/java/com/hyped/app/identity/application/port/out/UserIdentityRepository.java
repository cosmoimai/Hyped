package com.hyped.app.identity.application.port.out;

import com.hyped.app.identity.domain.IdentityProvider;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.identity.domain.UserIdentity;
import java.util.List;
import java.util.Optional;

public interface UserIdentityRepository {

    Optional<UserIdentity> findByProviderSubject(IdentityProvider provider, byte[] providerSubjectHmac);

    /** Returns candidates only; matching email never authorizes account linking or merging. */
    List<UserIdentity> findVerifiedByEmailHmac(byte[] emailHmac);

    boolean existsByUserIdAndProvider(UserId userId, IdentityProvider provider);

    UserIdentity save(UserIdentity identity);
}
