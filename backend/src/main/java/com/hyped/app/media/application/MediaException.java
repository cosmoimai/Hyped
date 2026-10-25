package com.hyped.app.media.application;

public final class MediaException extends RuntimeException {
    private final int status;
    private final String code;

    public MediaException(int status, String code) {
        super("The photo could not be processed. Please retry or select another photo.");
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static MediaException unavailable() {
        return new MediaException(503, "MEDIA_UNAVAILABLE");
    }
}
