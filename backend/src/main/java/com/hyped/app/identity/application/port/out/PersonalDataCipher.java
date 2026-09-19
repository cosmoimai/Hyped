package com.hyped.app.identity.application.port.out;

import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.domain.UserId;

/**
 * Implementations resolve usable per-user keys outside PostgreSQL backups.
 * Require an opaque, nonblank keyReference of at most 255 characters; never expose key material.
 * Bind ciphertext to the user, environment and field. Never retain caller-owned arrays;
 * return independent arrays and defensively copy any arrays stored in models.
 */
public interface PersonalDataCipher {
    byte[] encrypt(UserId userId, String keyReference, PersonalDataField field, byte[] plaintext);

    byte[] decrypt(UserId userId, String keyReference, PersonalDataField field, byte[] ciphertext);
}
