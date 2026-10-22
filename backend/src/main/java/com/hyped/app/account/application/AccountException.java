package com.hyped.app.account.application;

public final class AccountException extends RuntimeException {
    private final int status;
    private final String code;
    public AccountException(int status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
