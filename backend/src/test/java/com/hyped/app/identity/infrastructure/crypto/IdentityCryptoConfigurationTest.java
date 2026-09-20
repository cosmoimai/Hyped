package com.hyped.app.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.port.out.IdentityLookupProtector;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.application.port.out.PersonalDataKeyManager;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;

class IdentityCryptoConfigurationTest {
    @TempDir
    Path directory;
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(IdentityCryptoConfiguration.class);

    @Test
    void disabledNeedsNoKeyAndCreatesNoCryptoBeans() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(IdentityLookupProtector.class)
                .doesNotHaveBean(AesGcmEnvelopeCipher.class).doesNotHaveBean(PersonalDataCipher.class)
                .doesNotHaveBean(PersonalDataKeyManager.class));
    }

    @Test
    void enabledRejectsMissingKeyAndEnvironment() {
        runner.withPropertyValues("hyped.identity-crypto.enabled=true", "hyped.identity-crypto.environment=test")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("hyped.identity-crypto.enabled=true")
                .run(context -> assertThat(context).hasFailed());
        assertThatThrownBy(() -> new IdentityCryptoProperties(true, null, new ByteArrayResource(new byte[32])))
                .isInstanceOf(PersonalDataProtectionException.class);
    }

    @Test
    void loadsRawKeyFromResource() throws Exception {
        Path file = directory.resolve("test-key");
        Files.write(file, HmacSha256IdentityLookupProtectorTest.key());
        runner.withPropertyValues("hyped.identity-crypto.enabled=true", "hyped.identity-crypto.environment=test",
                "hyped.identity-crypto.lookup-hmac-key=" + file.toUri()).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(IdentityLookupProtector.class)
                            .doesNotHaveBean(PersonalDataCipher.class);
                    var protector = context.getBean(IdentityLookupProtector.class);
                    assertThat(protector.protectVerifiedEmail("person@example.com"))
                            .hasSize(32);
                });
    }

    @Test
    void rejectsShortKeyAndUnreadableResource() throws Exception {
        Path file = directory.resolve("short-key");
        Files.write(file, new byte[31]);
        runner.withPropertyValues("hyped.identity-crypto.enabled=true", "hyped.identity-crypto.environment=test",
                "hyped.identity-crypto.lookup-hmac-key=" + file.toUri())
                .run(context -> assertThat(context).hasFailed());
        Files.delete(file);
        runner.withPropertyValues("hyped.identity-crypto.enabled=true", "hyped.identity-crypto.environment=test",
                "hyped.identity-crypto.lookup-hmac-key=" + file.toUri())
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void configurationRedactsResourceAndEnvironment() {
        var properties = new IdentityCryptoProperties(true, "sensitive-environment",
                new ByteArrayResource(new byte[32], "sensitive-resource"));
        assertThat(properties.toString()).isEqualTo("IdentityCryptoProperties[enabled=true, configuration=[REDACTED]]");
    }
}
