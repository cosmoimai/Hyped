package com.hyped.app.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyped.app.policy.application.PolicyCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StagingPolicyPublicationTest {
    @ParameterizedTest
    @ValueSource(strings = {"terms", "privacy"})
    void newSafetyVersionIsBundledAndRendersTheConfiguredOperator(String kind) {
        var catalog = new PolicyCatalog("2026-10-26", "2026-10-26", "2026-10-26", true,
                "Staging Test Operator", "privacy@example.test");
        var document = catalog.document(kind, "2026-10-26");

        assertThat(catalog.available()).isTrue();
        assertThat(document.published()).isTrue();
        assertThat(document.version()).isEqualTo("2026-10-26");
        assertThat(document.content()).contains("Staging Test Operator", "privacy@example.test")
                .doesNotContain("{{operator}}", "{{contact}}");
    }

    @Test
    void bundlingTheNewVersionDoesNotActivateConsentBeforePublication() {
        var catalog = new PolicyCatalog("2026-10-26", "2026-10-26", "2026-10-26", false,
                "Staging Test Operator", "privacy@example.test");

        assertThat(catalog.available()).isFalse();
        assertThat(catalog.document("terms", "2026-10-26").published()).isFalse();
        assertThat(catalog.document("privacy", "2026-10-26").published()).isFalse();
    }
}
