package com.hyped.app.identity.infrastructure.firebase;

import com.google.firebase.ErrorCode;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.hyped.app.identity.application.exception.IdentityTokenVerificationException;
import com.hyped.app.identity.application.exception.IdentityTokenVerificationException.Reason;
import com.hyped.app.identity.application.model.VerifiedProviderIdentity;
import com.hyped.app.identity.application.port.out.IdentityTokenVerifier;

final class FirebaseIdentityTokenVerifier implements IdentityTokenVerifier {

    private final FirebaseAuth auth;
    private final FirebaseTokenClaimsMapper mapper;

    FirebaseIdentityTokenVerifier(FirebaseAuth auth, FirebaseTokenClaimsMapper mapper) {
        this.auth = auth;
        this.mapper = mapper;
    }

    @Override
    public VerifiedProviderIdentity verify(String firebaseIdToken) {
        if (firebaseIdToken == null || firebaseIdToken.isBlank()) {
            throw new IdentityTokenVerificationException(Reason.MISSING_TOKEN);
        }
        FirebaseToken decoded;
        try {
            // Firebase checks signature, issuer, project/audience, expiry and revocation.
            decoded = auth.verifyIdToken(firebaseIdToken, true);
        } catch (FirebaseAuthException exception) {
            throw new IdentityTokenVerificationException(reason(exception), exception);
        } catch (IllegalArgumentException exception) {
            throw new IdentityTokenVerificationException(Reason.INVALID_TOKEN, exception);
        } catch (RuntimeException exception) {
            throw new IdentityTokenVerificationException(Reason.VERIFIER_UNAVAILABLE, exception);
        }
        if (decoded == null) {
            throw new IdentityTokenVerificationException(Reason.INVALID_CLAIMS);
        }
        return mapper.map(decoded.getClaims());
    }

    private static Reason reason(FirebaseAuthException exception) {
        AuthErrorCode authCode = exception.getAuthErrorCode();
        if (authCode != null) {
            switch (authCode) {
                case EXPIRED_ID_TOKEN:
                    return Reason.EXPIRED_TOKEN;
                case REVOKED_ID_TOKEN:
                    return Reason.REVOKED_TOKEN;
                case CERTIFICATE_FETCH_FAILED, CONFIGURATION_NOT_FOUND:
                    return Reason.VERIFIER_UNAVAILABLE;
                case INVALID_ID_TOKEN, TENANT_ID_MISMATCH, USER_DISABLED, USER_NOT_FOUND:
                    return Reason.INVALID_TOKEN;
                default:
                    break;
            }
        }
        ErrorCode code = exception.getErrorCode();
        if (code == null) {
            return Reason.VERIFIER_UNAVAILABLE;
        }
        return switch (code) {
            case INVALID_ARGUMENT, OUT_OF_RANGE -> Reason.INVALID_TOKEN;
            default -> Reason.VERIFIER_UNAVAILABLE;
        };
    }
}
