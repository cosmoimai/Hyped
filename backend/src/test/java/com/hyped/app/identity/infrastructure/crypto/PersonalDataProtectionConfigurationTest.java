package com.hyped.app.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.application.port.out.PersonalDataKeyManager;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PersonalDataProtectionConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PersonalDataProtectionConfiguration.class)
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void disabledConfigurationStartsWithoutCredentialsOrCloudBeans() {
        try (var kms = mockStatic(KeyManagementServiceClient.class)) {
            runner.run(context -> assertThat(context).hasNotFailed()
                    .doesNotHaveBean(PersonalDataKeyManager.class)
                    .doesNotHaveBean(PersonalDataCipher.class)
                    .doesNotHaveBean(KeyManagementServiceClient.class)
                    .doesNotHaveBean(Firestore.class));
            kms.verifyNoInteractions();
        }
    }

    @Test
    void enabledConfigurationRequiresAllExternalLocations() {
        try (var kms = mockStatic(KeyManagementServiceClient.class)) {
            runner.withPropertyValues("hyped.personal-data.enabled=true")
                    .run(context -> assertThat(context).hasFailed());
            runner.withPropertyValues(
                    "hyped.personal-data.enabled=true",
                    "hyped.personal-data.environment=test",
                    "hyped.personal-data.kms.key-name=projects/test/locations/global/keyRings/r/cryptoKeys/k",
                    "hyped.personal-data.firestore.project-id=test-project",
                    "hyped.personal-data.firestore.database-id=key-registry")
                    .run(context -> assertThat(context).hasFailed());
            kms.verifyNoInteractions();
        }
    }

    @Test
    void propertiesRedactAllExternalConfiguration() {
        var properties = new PersonalDataProtectionProperties(true, "secret-environment",
                new PersonalDataProtectionProperties.Kms("secret-kms-name"),
                new PersonalDataProtectionProperties.Firestore(
                        "secret-project", "secret-database", "secret-collection"));
        assertThat(properties.toString()).isEqualTo(
                "PersonalDataProtectionProperties[enabled=true, configuration=[REDACTED]]");
        assertThat(properties.kms().toString()).isEqualTo("Kms[REDACTED]");
        assertThat(properties.firestore().toString()).isEqualTo("Firestore[REDACTED]");
    }
}
