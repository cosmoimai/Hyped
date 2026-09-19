package com.hyped.app.identity.infrastructure.crypto;

import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException.Reason;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

@ConfigurationProperties("hyped.identity-crypto")
public record IdentityCryptoProperties(@DefaultValue("false") boolean enabled,
        String environment, Resource lookupHmacKey) {
    public IdentityCryptoProperties {
        if (enabled && (environment == null || environment.isBlank() || lookupHmacKey == null)) {
            throw new PersonalDataProtectionException(Reason.INVALID_INPUT);
        }
    }

    @Override
    public String toString() {
        return "IdentityCryptoProperties[enabled=" + enabled + ", configuration=[REDACTED]]";
    }
}
