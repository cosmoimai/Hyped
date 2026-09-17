package com.hyped.app.identity.infrastructure.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.firebase.ErrorCode;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.hyped.app.identity.application.exception.IdentityTokenVerificationException;
import com.hyped.app.identity.application.exception.IdentityTokenVerificationException.Reason;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FirebaseIdentityTokenVerifierTest {
    private static final String RAW_TOKEN = "sensitive-raw-token";
    private final FirebaseAuth auth = mock(FirebaseAuth.class);
    private final FirebaseIdentityTokenVerifier verifier = new FirebaseIdentityTokenVerifier(auth,
            new FirebaseTokenClaimsMapper(FirebaseTokenClaimsMapperTest.CLOCK,
                    new FirebaseIdentityProperties(true, "test-project", Duration.ofSeconds(60))));

    @Test
    void delegatesToSdkWithRevocationEnabled() throws Exception {
        FirebaseToken decoded = mock(FirebaseToken.class);
        when(decoded.getClaims()).thenReturn(FirebaseTokenClaimsMapperTest.claims("google.com"));
        when(auth.verifyIdToken(RAW_TOKEN, true)).thenReturn(decoded);
        assertThat(verifier.verify(RAW_TOKEN).providerSubject()).isEqualTo("provider-subject");
        verify(auth).verifyIdToken(RAW_TOKEN, true);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingTokensBeforeSdk(String token) {
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOfSatisfying(
                IdentityTokenVerificationException.class,
                exception -> assertThat(exception.reason()).isEqualTo(Reason.MISSING_TOKEN));
        verifyNoInteractions(auth);
    }

    @ParameterizedTest
    @CsvSource({"EXPIRED_ID_TOKEN, EXPIRED_TOKEN", "REVOKED_ID_TOKEN, REVOKED_TOKEN",
            "INVALID_ID_TOKEN, INVALID_TOKEN", "TENANT_ID_MISMATCH, INVALID_TOKEN",
            "USER_DISABLED, INVALID_TOKEN", "CERTIFICATE_FETCH_FAILED, VERIFIER_UNAVAILABLE",
            "CONFIGURATION_NOT_FOUND, VERIFIER_UNAVAILABLE"})
    void mapsStableAuthCodes(AuthErrorCode code, Reason reason) throws Exception {
        var failure = new FirebaseAuthException(ErrorCode.INVALID_ARGUMENT,
                RAW_TOKEN + " provider-subject person@example.com", null, null, code);
        when(auth.verifyIdToken(RAW_TOKEN, true)).thenThrow(failure);
        assertSafeFailure(reason, failure);
    }

    @ParameterizedTest
    @CsvSource({"INVALID_ARGUMENT, INVALID_TOKEN", "UNAVAILABLE, VERIFIER_UNAVAILABLE",
            "UNAUTHENTICATED, VERIFIER_UNAVAILABLE", "PERMISSION_DENIED, VERIFIER_UNAVAILABLE",
            "DEADLINE_EXCEEDED, VERIFIER_UNAVAILABLE"})
    void mapsPlatformCodes(ErrorCode code, Reason reason) throws Exception {
        var failure = new FirebaseAuthException(code, RAW_TOKEN, null, null, null);
        when(auth.verifyIdToken(RAW_TOKEN, true)).thenThrow(failure);
        assertSafeFailure(reason, failure);
    }

    @Test
    void wrapsUnexpectedSdkFailureSafely() throws Exception {
        var failure = new IllegalStateException(RAW_TOKEN);
        when(auth.verifyIdToken(RAW_TOKEN, true)).thenThrow(failure);
        assertSafeFailure(Reason.VERIFIER_UNAVAILABLE, failure);
    }

    private void assertSafeFailure(Reason reason, Exception cause) {
        assertThatThrownBy(() -> verifier.verify(RAW_TOKEN)).isInstanceOfSatisfying(
                IdentityTokenVerificationException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(reason);
                    assertThat(exception.getMessage()).isEqualTo(reason.name());
                    assertThat(exception.toString())
                            .doesNotContain(RAW_TOKEN, "provider-subject", "person@example.com");
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }
}
