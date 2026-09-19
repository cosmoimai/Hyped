package com.hyped.app.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ApiProblemWriter {
    private static final String PROBLEM_BASE = "https://api.hyped.app/problems/";
    private final ObjectMapper objectMapper;

    public ApiProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ApiProblem create(
            HttpServletRequest request,
            int status,
            String code,
            String title,
            String detail,
            boolean retryable,
            List<ApiFieldError> fieldErrors,
            List<?> devices) {
        return new ApiProblem(PROBLEM_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-'),
                title, status, code, detail, requestId(request), fieldErrors, retryable, devices);
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String title,
            String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                create(request, status, code, title, detail, false, List.of(), List.of()));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestContextFilter.REQUEST_ID_ATTRIBUTE);
        return value instanceof String requestId ? requestId : UUIDHolder.newId();
    }

    private static final class UUIDHolder {
        private UUIDHolder() {
        }

        private static String newId() {
            return java.util.UUID.randomUUID().toString();
        }
    }
}
