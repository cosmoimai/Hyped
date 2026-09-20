package com.hyped.app.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;

public record ApiProblem(
        String type,
        String title,
        int status,
        String code,
        String detail,
        String requestId,
        List<ApiFieldError> fieldErrors,
        boolean retryable,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<?> devices) {

    public ApiProblem {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(requestId, "requestId");
        fieldErrors = List.copyOf(fieldErrors);
        devices = List.copyOf(devices);
    }
}
