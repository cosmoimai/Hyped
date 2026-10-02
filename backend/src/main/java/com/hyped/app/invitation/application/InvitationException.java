package com.hyped.app.invitation.application;

public class InvitationException extends RuntimeException {
    private final int status;
    private final String code;

    public InvitationException(int status, String code) {
        super("The invitation operation could not be completed.");
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static InvitationException invalid() {
        return new InvitationException(410, "INVITATION_INVALID");
    }
}
