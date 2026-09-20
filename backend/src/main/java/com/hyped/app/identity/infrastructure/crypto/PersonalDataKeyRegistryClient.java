package com.hyped.app.identity.infrastructure.crypto;

import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.Optional;

interface PersonalDataKeyRegistryClient {
    Optional<PersonalDataKeyRecord> find(UserId userId);

    Optional<UserId> findOwner(String keyReference);

    PersonalDataKeyRecord createIfAbsent(PersonalDataKeyRecord candidate);

    void destroy(UserId userId, String keyReference, Instant destroyedAt);
}
