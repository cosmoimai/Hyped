package com.hyped.app.identity.application.service;

import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserIdentity;
import com.hyped.app.profile.domain.UserProfile;
import java.util.Objects;

record PreparedNewAccount(UserAccount account, UserProfile profile, UserIdentity identity) {
    PreparedNewAccount {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(identity, "identity");
        if (!account.id().equals(profile.userId()) || !account.id().equals(identity.userId())) {
            throw new IllegalArgumentException("Prepared account ownership is inconsistent");
        }
    }

    @Override
    public String toString() {
        return "PreparedNewAccount[userId=" + account.id() + ", protected values=[REDACTED]]";
    }
}
