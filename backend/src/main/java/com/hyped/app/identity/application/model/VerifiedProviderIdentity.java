package com.hyped.app.identity.application.model;

import com.hyped.app.identity.domain.IdentityProvider;
import java.time.Instant;
import java.util.Objects;

/** Short-lived verified input for a future transaction, never a persistence or API model. */
public record VerifiedProviderIdentity(
        IdentityProvider provider,
        String providerSubject,
        String email,
        boolean emailVerified,
        String displayName,
        String photoUrl,
        Instant tokenIssuedAt,
        Instant authenticatedAt) {

    public VerifiedProviderIdentity {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerSubject, "providerSubject");
        Objects.requireNonNull(tokenIssuedAt, "tokenIssuedAt");
        Objects.requireNonNull(authenticatedAt, "authenticatedAt");
        providerSubject = providerSubject.strip();
        if (providerSubject.isBlank()) {
            throw new IllegalArgumentException("Provider subject is required");
        }
        requireBound(providerSubject, 255);
        email = normalizedOptional(email, 320);
        displayName = normalizedOptional(displayName, 100);
        if (photoUrl != null) {
            requireBound(photoUrl, 2048);
        }
        if (emailVerified && email == null) {
            throw new IllegalArgumentException("Verified email requires an email value");
        }
        if (authenticatedAt.isAfter(tokenIssuedAt)) {
            throw new IllegalArgumentException("Authentication time cannot follow token issuance");
        }
    }

    private static String normalizedOptional(String value, int limit) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        requireBound(normalized, limit);
        return normalized.isBlank() ? null : normalized;
    }

    private static void requireBound(String value, int limit) {
        if (value.codePointCount(0, value.length()) > limit) {
            throw new IllegalArgumentException("Identity field exceeds its allowed length");
        }
    }

    @Override
    public String toString() {
        return "VerifiedProviderIdentity[provider=" + provider + ", personal values=[REDACTED]]";
    }
}
