package com.hyped.app.identity.infrastructure.crypto;

import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException.Reason;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("hyped.personal-data")
public record PersonalDataProtectionProperties(
        @DefaultValue("false") boolean enabled,
        String environment,
        Kms kms,
        Firestore firestore) {

    public PersonalDataProtectionProperties {
        if (enabled && (invalid(environment) || kms == null || firestore == null
                || invalidLong(kms.keyName()) || invalid(firestore.projectId())
                || invalid(firestore.databaseId()) || invalid(firestore.collection())
                || firestore.collection().length() > 200)) {
            throw new PersonalDataProtectionException(Reason.INVALID_INPUT);
        }
    }

    private static boolean invalid(String value) {
        return value == null || value.isBlank() || !value.equals(value.strip()) || value.length() > 255;
    }

    private static boolean invalidLong(String value) {
        return value == null || value.isBlank() || !value.equals(value.strip()) || value.length() > 1024;
    }

    @Override
    public String toString() {
        return "PersonalDataProtectionProperties[enabled=" + enabled + ", configuration=[REDACTED]]";
    }

    public record Kms(String keyName) {
        @Override
        public String toString() {
            return "Kms[REDACTED]";
        }
    }

    /** The database must be dedicated to key metadata and excluded from scheduled exports and backups. */
    public record Firestore(String projectId, String databaseId, String collection) {
        @Override
        public String toString() {
            return "Firestore[REDACTED]";
        }
    }
}
