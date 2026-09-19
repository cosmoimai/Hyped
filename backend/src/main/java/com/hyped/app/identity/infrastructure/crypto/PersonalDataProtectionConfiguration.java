package com.hyped.app.identity.infrastructure.crypto;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException.Reason;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "hyped.personal-data", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(PersonalDataProtectionProperties.class)
public class PersonalDataProtectionConfiguration {

    @Bean(destroyMethod = "close")
    KeyManagementServiceClient personalDataKmsServiceClient(PersonalDataProtectionProperties properties) {
        try {
            return KeyManagementServiceClient.create();
        } catch (IOException | RuntimeException exception) {
            throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
        }
    }

    @Bean(destroyMethod = "close")
    Firestore personalDataKeyFirestore(PersonalDataProtectionProperties properties) {
        try {
            return FirestoreOptions.newBuilder()
                    .setProjectId(properties.firestore().projectId())
                    .setDatabaseId(properties.firestore().databaseId())
                    .build()
                    .getService();
        } catch (RuntimeException exception) {
            throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
        }
    }

    @Bean
    PersonalDataKmsClient personalDataKmsClient(KeyManagementServiceClient personalDataKmsServiceClient) {
        return new GoogleCloudKmsClient(personalDataKmsServiceClient);
    }

    @Bean
    PersonalDataKeyRegistryClient personalDataKeyRegistryClient(
            Firestore personalDataKeyFirestore, PersonalDataProtectionProperties properties) {
        return new FirestorePersonalDataKeyRegistryClient(
                personalDataKeyFirestore, properties.firestore().collection());
    }

    @Bean
    KmsFirestorePersonalDataProtectionAdapter personalDataProtectionAdapter(
            PersonalDataKmsClient kms,
            PersonalDataKeyRegistryClient registry,
            PersonalDataProtectionProperties properties,
            Clock clock) {
        return new KmsFirestorePersonalDataProtectionAdapter(
                kms, registry, new AesGcmEnvelopeCipher(), properties, clock, new SecureRandom());
    }
}
