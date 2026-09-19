package com.hyped.app.profile.application.port.out;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.profile.domain.UserProfile;
import java.util.Optional;

public interface UserProfileRepository {

    Optional<UserProfile> findByUserId(UserId userId);

    UserProfile save(UserProfile profile);

    /**
     * Atomically replaces the protected profile fields and increments the stored revision.
     * Returns empty when the expected revision no longer matches or the profile does not exist.
     */
    Optional<UserProfile> update(UserProfile profile, long expectedProfileRevision);
}
