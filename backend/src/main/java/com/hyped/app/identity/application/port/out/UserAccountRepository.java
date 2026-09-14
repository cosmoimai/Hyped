package com.hyped.app.identity.application.port.out;

import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import java.util.Optional;

public interface UserAccountRepository {

    Optional<UserAccount> findById(UserId userId);

    /**
     * Requires an active transaction. The account row remains locked until that transaction ends.
     */
    Optional<UserAccount> findByIdForUpdate(UserId userId);

    UserAccount save(UserAccount account);
}
