package com.hyped.app.invitation.application;

/** A validated terminal rejection recorded before any domain mutation; its replay record must commit. */
public final class RecordedInvitationException extends InvitationException {
    public RecordedInvitationException(InvitationException failure) {
        super(failure.status(), failure.code());
    }
}
