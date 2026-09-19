package com.hyped.app.identity.infrastructure.crypto;

import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException.Reason;
import com.hyped.app.identity.domain.UserId;

record EncryptionContext(String environment, UserId userId, String table, String column) {
    EncryptionContext {
        if (userId == null || invalid(environment) || invalid(table) || invalid(column)) {
            throw new PersonalDataProtectionException(Reason.INVALID_INPUT);
        }
    }

    private static boolean invalid(String value) {
        return value == null || value.isBlank() || value.length() > 255;
    }

    byte[] associatedData(int version) {
        return CryptoEncoding.encode("hyped", environment, userId.toString(), table, column, Integer.toString(version));
    }

    @Override
    public String toString() {
        return "EncryptionContext[REDACTED]";
    }
}
