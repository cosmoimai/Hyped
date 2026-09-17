package com.hyped.app.identity.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.domain.IdentityProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VerifiedProviderIdentityTest {
    private static final Instant TIME = Instant.parse("2026-09-17T10:00:00Z");

    @Test
    void rejectsNullRequiredFields() {
        assertThatThrownBy(() -> identity(null, "subject", TIME, TIME)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> identity(IdentityProvider.GOOGLE, null, TIME, TIME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> identity(IdentityProvider.GOOGLE, "subject", null, TIME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> identity(IdentityProvider.GOOGLE, "subject", TIME, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void validatesSubjectAndTimeOrder() {
        assertThatThrownBy(() -> identity(IdentityProvider.GOOGLE, " ", TIME, TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> identity(IdentityProvider.GOOGLE, "x".repeat(256), TIME, TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> identity(IdentityProvider.GOOGLE, "subject", TIME, TIME.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(identity(IdentityProvider.GOOGLE, " subject ", TIME, TIME).providerSubject()).isEqualTo("subject");
    }

    private VerifiedProviderIdentity identity(IdentityProvider provider, String subject, Instant issued, Instant auth) {
        return new VerifiedProviderIdentity(provider, subject, null, false, null, null, issued, auth);
    }
}
