package com.hyped.app.notification.application;

import com.hyped.app.identity.application.model.PersonalDataField;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.notification.application.port.out.PushRegistrationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "hyped.push", name = "enabled", havingValue = "true")
public class PushRegistrationService {
    private final PushRegistrationRepository registrations;
    private final PersonalDataCipher cipher;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PushRegistrationService(PushRegistrationRepository registrations, PersonalDataCipher cipher, Clock clock,
            PlatformTransactionManager transactions) {
        this.registrations = registrations;
        this.cipher = cipher;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactions);
    }

    @Transactional(propagation = Propagation.NEVER)
    public void register(UUID user, UUID session, UUID installation, String token, boolean enabled) {
        if (!enabled) {
            transactions.executeWithoutResult(status -> {
                var binding = registrations.lock(user, session, installation, clock.instant());
                registrations.replace(binding, session, installation, null, null, clock.instant());
            });
            return;
        }
        if (token == null || token.isBlank() || token.length() > 4096
                || token.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Invalid push registration");
        }
        var binding = transactions.execute(status ->
                registrations.lock(user, session, installation, clock.instant()));
        // External key operations must not retain a database transaction or row locks.
        byte[] plaintext = token.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] encrypted = cipher.encrypt(new UserId(user), binding.keyReference(),
                    PersonalDataField.DEVICE_FCM_TOKEN, plaintext);
            byte[] fingerprint = MessageDigest.getInstance("SHA-256").digest(plaintext);
            transactions.executeWithoutResult(status -> {
                var current = registrations.lock(user, session, installation, clock.instant());
                // Logout, expiry, account change or key replacement may occur during encryption.
                if (!current.equals(binding)) {
                    throw new PushRegistrationException();
                }
                registrations.replace(current, session, installation, encrypted, fingerprint, clock.instant());
            });
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Push registration unavailable");
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }
}
