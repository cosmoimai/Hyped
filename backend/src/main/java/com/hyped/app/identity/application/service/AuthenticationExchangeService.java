package com.hyped.app.identity.application.service;

import com.hyped.app.identity.application.model.AuthenticationExchangeCommand;
import com.hyped.app.identity.application.model.AuthenticationExchangeResult;
import com.hyped.app.identity.application.model.CreateSessionCommand;
import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.application.model.ProvisionedPersonalDataKey;
import com.hyped.app.identity.application.model.VerifiedProviderIdentity;
import com.hyped.app.identity.application.port.out.IdGenerator;
import com.hyped.app.identity.application.port.out.IdentityLookupProtector;
import com.hyped.app.identity.application.port.out.IdentityTokenVerifier;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.application.port.out.PersonalDataKeyManager;
import com.hyped.app.identity.application.port.out.UserIdentityRepository;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.IdentityId;
import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.identity.domain.UserIdentity;
import com.hyped.app.profile.domain.UserProfile;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public class AuthenticationExchangeService {
    static final String DEFAULT_DISPLAY_NAME = "Hyped User";
    private static final int DISPLAY_NAME_LIMIT = 80;

    private final IdentityTokenVerifier verifier;
    private final IdentityLookupProtector lookupProtector;
    private final UserIdentityRepository identities;
    private final PersonalDataKeyManager keyManager;
    private final PersonalDataCipher personalDataCipher;
    private final AuthenticationExchangeTransactionService transactions;
    private final IdGenerator ids;
    private final Clock clock;

    public AuthenticationExchangeService(
            IdentityTokenVerifier verifier,
            IdentityLookupProtector lookupProtector,
            UserIdentityRepository identities,
            PersonalDataKeyManager keyManager,
            PersonalDataCipher personalDataCipher,
            AuthenticationExchangeTransactionService transactions,
            IdGenerator ids,
            Clock clock) {
        this.verifier = verifier;
        this.lookupProtector = lookupProtector;
        this.identities = identities;
        this.keyManager = keyManager;
        this.personalDataCipher = personalDataCipher;
        this.transactions = transactions;
        this.ids = ids;
        this.clock = clock;
    }

    public AuthenticationExchangeResult exchange(AuthenticationExchangeCommand command) {
        Objects.requireNonNull(command, "command");
        VerifiedProviderIdentity verified = verifier.verify(command.firebaseIdToken());
        byte[] subjectHmac = lookupProtector.protectProviderSubject(
                verified.provider(), verified.providerSubject());
        var existing = identities.findByProviderSubject(verified.provider(), subjectHmac);
        if (existing.isPresent()) {
            return exchangeExisting(verified, subjectHmac, command, existing.orElseThrow().userId());
        }

        byte[] emailHmac = verified.emailVerified()
                ? lookupProtector.protectVerifiedEmail(verified.email()) : null;
        if (emailHmac != null && !identities.findVerifiedByEmailHmac(emailHmac).isEmpty()) {
            return new AuthenticationExchangeResult.AccountLinkConfirmationRequired();
        }
        return createAccount(verified, subjectHmac, emailHmac, command);
    }

    private AuthenticationExchangeResult createAccount(
            VerifiedProviderIdentity verified,
            byte[] subjectHmac,
            byte[] emailHmac,
            AuthenticationExchangeCommand command) {
        UserId userId = new UserId(ids.generate());
        ProvisionedPersonalDataKey key = keyManager.provisionKey(userId);
        PreparedNewAccount prepared;
        try {
            prepared = prepareAccount(verified, subjectHmac, emailHmac, userId, key.keyReference());
        } catch (RuntimeException exception) {
            cleanupKey(userId, key, exception);
            throw exception;
        }

        CreateSessionCommand sessionCommand = sessionCommand(command, userId);
        try {
            AuthenticationExchangeResult result = transactions.createNew(prepared, sessionCommand);
            if (result instanceof AuthenticationExchangeResult.AccountLinkConfirmationRequired) {
                cleanupKey(userId, key, null);
            }
            return result;
        } catch (ProviderIdentityRaceException exception) {
            cleanupKey(userId, key, exception);
            return transactions.exchangeExisting(verified.provider(), subjectHmac,
                            sessionCommand(command, winningUserId(verified, subjectHmac)))
                    .orElseThrow(() -> new IllegalStateException("Concurrent identity winner is unavailable"));
        } catch (RuntimeException exception) {
            cleanupKey(userId, key, exception);
            throw exception;
        }
    }

    private UserId winningUserId(VerifiedProviderIdentity verified, byte[] subjectHmac) {
        return identities.findByProviderSubject(verified.provider(), subjectHmac)
                .orElseThrow(() -> new IllegalStateException("Concurrent identity winner is unavailable"))
                .userId();
    }

    private AuthenticationExchangeResult exchangeExisting(
            VerifiedProviderIdentity verified,
            byte[] subjectHmac,
            AuthenticationExchangeCommand command,
            UserId userId) {
        return transactions.exchangeExisting(
                        verified.provider(), subjectHmac, sessionCommand(command, userId))
                .orElseGet(() -> new AuthenticationExchangeResult.AccountUnavailable(AccountStatus.DELETED, null));
    }

    private PreparedNewAccount prepareAccount(
            VerifiedProviderIdentity verified,
            byte[] subjectHmac,
            byte[] emailHmac,
            UserId userId,
            String keyReference) {
        Instant now = clock.instant();
        byte[] displayNameCiphertext = encryptText(
                userId, keyReference, PersonalDataField.USER_PROFILE_DISPLAY_NAME,
                displayName(verified.displayName()));
        byte[] providerPhotoCiphertext = encryptNullable(
                userId, keyReference, PersonalDataField.USER_PROFILE_PROVIDER_PHOTO_URL, verified.photoUrl());
        byte[] emailCiphertext = verified.emailVerified()
                ? encryptText(userId, keyReference, PersonalDataField.USER_IDENTITY_EMAIL, verified.email()) : null;
        try {
            UserAccount account = new UserAccount(userId, AccountStatus.ACTIVE, null, 0, keyReference,
                    null, null, null, now, now);
            UserProfile profile = new UserProfile(userId, displayNameCiphertext, providerPhotoCiphertext,
                    null, 1, now, now);
            UserIdentity identity = new UserIdentity(new IdentityId(ids.generate()), userId, verified.provider(),
                    subjectHmac, emailCiphertext, emailHmac, verified.emailVerified(), now, now, now);
            return new PreparedNewAccount(account, profile, identity);
        } finally {
            Arrays.fill(displayNameCiphertext, (byte) 0);
            clear(providerPhotoCiphertext);
            clear(emailCiphertext);
        }
    }

    private byte[] encryptNullable(
            UserId userId, String keyReference, PersonalDataField field, String value) {
        return value == null ? null : encryptText(userId, keyReference, field, value);
    }

    private byte[] encryptText(UserId userId, String keyReference, PersonalDataField field, String value) {
        byte[] plaintext = value.getBytes(StandardCharsets.UTF_8);
        try {
            return personalDataCipher.encrypt(userId, keyReference, field, plaintext);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private static String displayName(String providerDisplayName) {
        String normalized = providerDisplayName == null
                ? DEFAULT_DISPLAY_NAME : Normalizer.normalize(providerDisplayName.strip(), Normalizer.Form.NFC);
        if (normalized.isBlank()) {
            return DEFAULT_DISPLAY_NAME;
        }
        int count = normalized.codePointCount(0, normalized.length());
        return count <= DISPLAY_NAME_LIMIT
                ? normalized : normalized.substring(0, normalized.offsetByCodePoints(0, DISPLAY_NAME_LIMIT));
    }

    private static CreateSessionCommand sessionCommand(AuthenticationExchangeCommand command, UserId userId) {
        return new CreateSessionCommand(userId, command.installationId(), command.platform(), command.deviceName(),
                null, null, false);
    }

    private void cleanupKey(UserId userId, ProvisionedPersonalDataKey key, RuntimeException originalFailure) {
        if (!key.createdByRequest()) {
            return;
        }
        try {
            keyManager.destroyKey(userId, key.keyReference());
        } catch (RuntimeException cleanupFailure) {
            if (originalFailure != null) {
                cleanupFailure.addSuppressed(originalFailure);
            }
            throw cleanupFailure;
        }
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }
}
