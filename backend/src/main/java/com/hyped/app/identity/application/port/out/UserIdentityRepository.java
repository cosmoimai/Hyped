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

    /**
     * Inserts without replacing an identity. Returns false when the authoritative provider-subject
     * uniqueness constraint is already owned by a concurrent transaction. Requires an active transaction.
     */
    boolean createIfProviderSubjectAbsent(UserIdentity identity);

    UserIdentity save(UserIdentity identity);
}
