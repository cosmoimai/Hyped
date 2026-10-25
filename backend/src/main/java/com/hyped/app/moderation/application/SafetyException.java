package com.hyped.app.moderation.application;

/** Public diagnostics contain only controlled codes and fixed safe text. */
public final class SafetyException extends RuntimeException {
    private final int status;
    private final String code;

    public SafetyException(int status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
