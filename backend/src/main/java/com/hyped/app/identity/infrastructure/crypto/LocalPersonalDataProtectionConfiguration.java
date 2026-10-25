package com.hyped.app.identity.infrastructure.crypto;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Explicit single-process, loopback-only development support; never a cloud fallback. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "hyped.personal-data", name = "provider", havingValue = "local")
public class LocalPersonalDataProtectionConfiguration {
    @Bean(destroyMethod = "close")
    LocalPersonalDataStore localPersonalDataStore(Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of("local"))
                || environment.acceptsProfiles(Profiles.of("prod", "production", "staging"))
                || !"true".equals(environment.getProperty("hyped.personal-data.enabled"))
                || !"127.0.0.1".equals(environment.getProperty("server.address"))) {
            throw new IllegalStateException("Local key storage requires the local profile and loopback binding");
        }
        String directory = environment.getProperty("hyped.personal-data.local-directory");
        if (directory == null || directory.isBlank()) {
            throw new IllegalStateException("Local key storage requires a private directory outside Git");
        }
        return new LocalPersonalDataStore(Path.of(directory));
    }

    @Bean
    KmsFirestorePersonalDataProtectionAdapter localPersonalDataProtectionAdapter(
            LocalPersonalDataStore store, Clock clock) {
        // Reuse the same per-user envelope encryption, field binding and destruction semantics.
        // These identifiers are local context labels, never Google Cloud resource names.
        var properties = new PersonalDataProtectionProperties(true, "local-development",
                new PersonalDataProtectionProperties.Kms("local-development-wrapping-key"),
                new PersonalDataProtectionProperties.Firestore("local", "local", "local"));
        return new KmsFirestorePersonalDataProtectionAdapter(store, store,
                new AesGcmEnvelopeCipher(), properties, clock, new SecureRandom());
    }
}
