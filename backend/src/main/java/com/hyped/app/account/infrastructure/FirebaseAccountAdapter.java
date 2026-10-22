package com.hyped.app.account.infrastructure;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.hyped.app.account.application.port.out.FirebaseAccountAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "hyped.firebase", name = "enabled", havingValue = "true")
public class FirebaseAccountAdapter implements FirebaseAccountAdmin {
    private final FirebaseAuth auth;
    public FirebaseAccountAdapter(FirebaseAuth auth) { this.auth = auth; }
    @Override
    public String verifiedUid(String token) {
        try { return auth.verifyIdToken(token, true).getUid(); }
        catch (FirebaseAuthException | RuntimeException failure) { throw unavailable(); }
    }
    @Override
    public void delete(String uid) {
        try { auth.deleteUser(uid); }
        catch (FirebaseAuthException failure) {
            if (failure.getAuthErrorCode() != AuthErrorCode.USER_NOT_FOUND) { throw unavailable(); }
        } catch (RuntimeException failure) { throw unavailable(); }
    }
    private static com.hyped.app.account.application.AccountException unavailable() {
        return new com.hyped.app.account.application.AccountException(503, "DEPENDENCY_UNAVAILABLE",
                "Account cleanup is temporarily unavailable");
    }
}
