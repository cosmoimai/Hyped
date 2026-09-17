package com.hyped.app.identity.infrastructure.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.hyped.app.identity.application.exception.IdentityTokenVerificationException;
import com.hyped.app.identity.application.exception.IdentityTokenVerificationException.Reason;
import com.hyped.app.identity.application.port.out.IdentityTokenVerifier;
import java.io.IOException;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "hyped.firebase", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(FirebaseIdentityProperties.class)
public class FirebaseIdentityConfiguration {

    @Bean(destroyMethod = "delete")
    FirebaseApp firebaseIdentityApp(FirebaseIdentityProperties properties) {
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setProjectId(properties.projectId())
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build();
            return FirebaseApp.initializeApp(options, "hyped-identity");
        } catch (IOException | RuntimeException exception) {
            throw new IdentityTokenVerificationException(Reason.VERIFIER_UNAVAILABLE, exception);
        }
    }

    @Bean
    FirebaseAuth firebaseIdentityAuth(FirebaseApp firebaseIdentityApp) {
        return FirebaseAuth.getInstance(firebaseIdentityApp);
    }

    @Bean
    FirebaseTokenClaimsMapper firebaseTokenClaimsMapper(Clock clock, FirebaseIdentityProperties properties) {
        return new FirebaseTokenClaimsMapper(clock, properties);
    }

    @Bean
    IdentityTokenVerifier identityTokenVerifier(FirebaseAuth firebaseIdentityAuth, FirebaseTokenClaimsMapper mapper) {
        return new FirebaseIdentityTokenVerifier(firebaseIdentityAuth, mapper);
    }
}
