package com.hyped.app.identity.application.service;

import com.hyped.app.identity.application.model.AuthenticationUserProfile;
import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.profile.application.port.out.UserProfileRepository;
import com.hyped.app.profile.domain.UserProfile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "true")
public class AuthenticationProfileReader {
    private final UserAccountRepository accounts;
    private final UserProfileRepository profiles;
    private final PersonalDataCipher cipher;

    public AuthenticationProfileReader(
            UserAccountRepository accounts,
            UserProfileRepository profiles,
            PersonalDataCipher cipher) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.cipher = cipher;
    }

    public AuthenticationUserProfile read(UserId userId) {
        UserAccount account = accounts.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authentication account is unavailable"));
        UserProfile profile = profiles.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Authentication profile is unavailable"));
        String displayName = decrypt(userId, account.piiKeyReference(),
                PersonalDataField.USER_PROFILE_DISPLAY_NAME, profile.displayNameCiphertext());
        String providerPhotoUrl = profile.providerPhotoUrlCiphertext() == null ? null
                : decrypt(userId, account.piiKeyReference(), PersonalDataField.USER_PROFILE_PROVIDER_PHOTO_URL,
                        profile.providerPhotoUrlCiphertext());
        return new AuthenticationUserProfile(userId, displayName, profile.photoMediaId(),
                providerPhotoUrl, profile.profileRevision());
    }

    private String decrypt(UserId userId, String keyReference, PersonalDataField field, byte[] ciphertext) {
        byte[] plaintext = cipher.decrypt(userId, keyReference, field, ciphertext);
        try {
            return new String(plaintext, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }
}
