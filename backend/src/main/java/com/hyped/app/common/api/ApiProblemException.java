package com.hyped.app.common.api;

import java.util.List;
import org.springframework.http.HttpStatus;

public final class ApiProblemException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String title;
    private final boolean retryable;
    private final List<?> devices;

    public ApiProblemException(HttpStatus status, String code, String title, String detail) {
        this(status, code, title, detail, false, List.of());
    }

    public ApiProblemException(
            HttpStatus status,
            String code,
            String title,
            String detail,
            boolean retryable,
            List<?> devices) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
        this.retryable = retryable;
        this.devices = List.copyOf(devices);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public boolean retryable() {
        return retryable;
    }

    public List<?> devices() {
        return devices;
    }
}
