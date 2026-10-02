package com.hyped.app.invitation.application.port.out;

public interface InvitationCryptography {
    String newCode();

    String newToken();

    byte[] digest(String purpose, String value);

    byte[] encrypt(String context, String value);

    String decrypt(String context, byte[] ciphertext);

    String inviteUrl(String token);
}
