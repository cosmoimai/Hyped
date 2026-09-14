package com.hyped.app.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.hyped.app.identity.application.port.out.AccessTokenIssuer;
import com.hyped.app.identity.application.port.out.RefreshTokenDigester;
import com.hyped.app.identity.application.port.out.RefreshTokenGenerator;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

class TokenConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TokenConfiguration.class)
            .withBean(Clock.class, () -> Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void defaultsSupportPersistenceOnlyStartupWithoutSigningKeys() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(AccessTokenIssuer.class);
            assertThat(context).hasSingleBean(RefreshTokenGenerator.class).hasSingleBean(RefreshTokenDigester.class);
            TokenProperties properties = context.getBean(TokenProperties.class);
            assertThat(properties.accessTokenLifetime()).isEqualTo(Duration.ofHours(1));
            assertThat(properties.refreshTokenLifetime()).isEqualTo(Duration.ofDays(30));
        });
    }

    @Test
    void signingConfigurationBindsInMemoryKeysAndWiresIssuer() {
        runner.withInitializer(context -> ((GenericApplicationContext) context)
                .setResourceLoader(new DefaultResourceLoader() {
                    @Override
                    public Resource getResource(String location) {
                        return switch (location) {
                            case "memory:public" -> TestTokenKeys.PUBLIC_KEY;
                            case "memory:private" -> TestTokenKeys.PRIVATE_KEY;
                            default -> super.getResource(location);
                        };
                    }
                }))
                .withPropertyValues("hyped.tokens.enabled=true", "hyped.tokens.issuer=https://issuer.example.test",
                        "hyped.tokens.audience=hyped-mobile", "hyped.tokens.key-id=test-key-1",
                        "hyped.tokens.public-key=memory:public", "hyped.tokens.private-key=memory:private")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AccessTokenIssuer.class);
                    assertThat(context.getBean(AccessTokenIssuer.class).issue(new UserId(UUID.randomUUID()),
                            new SessionId(UUID.randomUUID()), new InstallationId(UUID.randomUUID())).tokenValue())
                            .isNotBlank();
                });
    }

    @Test
    void enabledSigningFailsWithoutRequiredSettings() {
        runner.withPropertyValues("hyped.tokens.enabled=true").run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s", "500ms"})
    void rejectsInvalidTokenLifetimes(String lifetime) {
        runner.withPropertyValues("hyped.tokens.access-token-lifetime=" + lifetime)
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("hyped.tokens.refresh-token-lifetime=" + lifetime)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsInvalidPrivateKeyMaterial() {
        TokenProperties properties = TestTokenKeys.properties();
        // A public key cannot be parsed as PKCS#8 private key material.
        TokenProperties invalid = new TokenProperties(true, properties.issuer(), properties.audience(),
                properties.keyId(), TestTokenKeys.PUBLIC_KEY, TestTokenKeys.PUBLIC_KEY,
                properties.accessTokenLifetime(), properties.refreshTokenLifetime());
        assertThatIllegalArgumentException().isThrownBy(() -> new TokenConfiguration().jwtEncoder(invalid));
    }
}
