package com.hyped.app.identity.application.port.out;

import com.hyped.app.identity.application.model.ProvisionedPersonalDataKey;
import com.hyped.app.identity.domain.UserId;

/**
 * Implementations must keep usable per-user keys outside PostgreSQL backups.
 * References are opaque, nonblank strings of at most 255 characters, never key material.
 * Implementations must validate references without interpreting or normalizing them.
 */
public interface PersonalDataKeyManager {
    /**
     * Provisions or returns the existing external key reference before a database transaction starts.
     * A future account-creation orchestrator must call destroyKey when its later transaction fails and
     * the account has no previously committed key reference.
     */
    default String createKey(UserId userId) {
        return provisionKey(userId).keyReference();
    }

    /** Reports whether this call created the external record so cleanup cannot destroy a concurrent winner. */
    ProvisionedPersonalDataKey provisionKey(UserId userId);

    /**
     * Idempotent cleanup and cryptographic destruction path. A missing key is already destroyed and
     * must never be recreated.
     */
    void destroyKey(UserId userId, String keyReference);
}
