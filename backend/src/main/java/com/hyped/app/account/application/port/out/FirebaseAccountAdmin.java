package com.hyped.app.account.application.port.out;

public interface FirebaseAccountAdmin {
    String verifiedUid(String token);
    void delete(String uid);
}
