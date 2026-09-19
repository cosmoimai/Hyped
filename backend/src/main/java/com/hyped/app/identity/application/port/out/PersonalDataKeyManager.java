package com.hyped.app.identity.application.port.out;

import com.hyped.app.identity.domain.UserId;

/**
 * Implementations must keep usable per-user keys outside PostgreSQL backups.
 * References are opaque, nonblank strings of at most 255 characters, never key material.
 * Implementations must validate references without interpreting or normalizing them.
 */
public interface PersonalDataKeyManager {
    String createKey(UserId userId);

    /** Idempotent: a missing key is already destroyed and must never be recreated. */
    void destroyKey(UserId userId, String keyReference);
}
