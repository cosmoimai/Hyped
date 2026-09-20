package com.hyped.app.identity.infrastructure.crypto;

import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException.Reason;
import com.hyped.app.identity.application.port.out.IdentityLookupProtector;
import com.hyped.app.identity.domain.IdentityProvider;
import java.security.GeneralSecurityException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacSha256IdentityLookupProtector implements IdentityLookupProtector {
    private final SecretKeySpec key;

    public HmacSha256IdentityLookupProtector(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length < 32) {
            throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
        }
        key = new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Override
    public byte[] protectProviderSubject(IdentityProvider provider, String providerSubject) {
        if (provider == null) {
            throw new PersonalDataProtectionException(Reason.INVALID_INPUT);
        }
        String subject = validate(providerSubject, 255);
        return authenticate(CryptoEncoding.encode("hyped.identity.provider-subject.v1",
                provider.name().toLowerCase(Locale.ROOT), subject));
    }

    @Override
    public byte[] protectVerifiedEmail(String email) {
        if (email == null) {
            throw new PersonalDataProtectionException(Reason.INVALID_INPUT);
        }
        String normalized = Normalizer.normalize(email.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return authenticate(CryptoEncoding.encode("hyped.identity.verified-email.v1", validate(normalized, 320)));
    }

    private static String validate(String value, int maximum) {
        if (value == null) {
            throw new PersonalDataProtectionException(Reason.INVALID_INPUT);
        }
        String normalized = value.strip();
        if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > maximum) {
            throw new PersonalDataProtectionException(Reason.INVALID_INPUT);
        }
        return normalized;
    }

    private byte[] authenticate(byte[] input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
        } finally {
            Arrays.fill(input, (byte) 0);
        }
    }
}
