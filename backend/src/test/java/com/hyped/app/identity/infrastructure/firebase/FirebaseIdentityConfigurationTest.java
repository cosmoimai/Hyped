package com.hyped.app.identity.infrastructure.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.hyped.app.identity.application.port.out.IdentityTokenVerifier;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FirebaseIdentityConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FirebaseIdentityConfiguration.class)
            .withBean(Clock.class, () -> FirebaseTokenClaimsMapperTest.CLOCK);

    @Test
    void disabledConfigurationDoesNotAccessCredentialsOrCreateFirebaseBeans() {
        try (var credentials = mockStatic(GoogleCredentials.class); var apps = mockStatic(FirebaseApp.class)) {
            runner.run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(FirebaseApp.class)
                        .doesNotHaveBean(FirebaseAuth.class).doesNotHaveBean(IdentityTokenVerifier.class)
                        .doesNotHaveBean(FirebaseTokenClaimsMapper.class);
            });
            credentials.verifyNoInteractions();
            apps.verifyNoInteractions();
        }
    }

    @Test
    void enabledConfigurationRequiresExplicitProjectBeforeCredentials() {
        try (var credentials = mockStatic(GoogleCredentials.class); var apps = mockStatic(FirebaseApp.class)) {
            runner.withPropertyValues("hyped.firebase.enabled=true").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
            });
            credentials.verifyNoInteractions();
            apps.verifyNoInteractions();
        }
    }

    @Test
    void initializesNamedAppWithAdcAndExplicitProjectAndDeletesOnShutdown() {
        FirebaseApp app = mock(FirebaseApp.class);
        FirebaseAuth auth = mock(FirebaseAuth.class);
        GoogleCredentials adc = mock(GoogleCredentials.class);
        try (var credentials = mockStatic(GoogleCredentials.class);
                var apps = mockStatic(FirebaseApp.class); var authentication = mockStatic(FirebaseAuth.class)) {
            credentials.when(GoogleCredentials::getApplicationDefault).thenReturn(adc);
            apps.when(() -> FirebaseApp.initializeApp(any(FirebaseOptions.class), eq("hyped-identity")))
                    .thenAnswer(invocation -> {
                        FirebaseOptions options = invocation.getArgument(0);
                        assertThat(options.getProjectId()).isEqualTo("explicit-test-project");
                        return app;
                    });
            authentication.when(() -> FirebaseAuth.getInstance(app)).thenReturn(auth);
            runner.withPropertyValues("hyped.firebase.enabled=true", "hyped.firebase.project-id=explicit-test-project")
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(IdentityTokenVerifier.class);
                        assertThat(context.getBean(FirebaseIdentityProperties.class).clockSkew())
                                .isEqualTo(Duration.ofSeconds(60));
                    });
            credentials.verify(GoogleCredentials::getApplicationDefault);
        }
        verify(app).delete();
    }
}
