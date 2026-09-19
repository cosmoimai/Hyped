package com.hyped.app.common.api;

import com.hyped.app.identity.application.exception.IdentityTokenVerificationException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalProblemHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalProblemHandler.class);
    private final ApiProblemWriter problems;

    public GlobalProblemHandler(ApiProblemWriter problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ApiProblemException.class)
    ResponseEntity<ApiProblem> apiProblem(ApiProblemException exception, HttpServletRequest request) {
        return response(request, exception.status(), exception.code(), exception.title(),
                exception.getMessage(), exception.retryable(), List.of(), exception.devices());
    }

    @ExceptionHandler(IdentityTokenVerificationException.class)
    ResponseEntity<ApiProblem> invalidIdentityToken(
            IdentityTokenVerificationException exception, HttpServletRequest request) {
        if (exception.reason() == IdentityTokenVerificationException.Reason.VERIFIER_UNAVAILABLE) {
            return response(request, HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_VERIFIER_UNAVAILABLE",
                    "Identity provider unavailable", "Identity verification is temporarily unavailable.",
                    true, List.of(), List.of());
        }
        return response(request, HttpStatus.UNAUTHORIZED, "IDENTITY_TOKEN_INVALID",
                "Identity token invalid", "The identity token is invalid or expired.",
                false, List.of(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiFieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), validationCode(error.getCode()),
                        safeValidationMessage(error.getCode())))
                .sorted(Comparator.comparing(ApiFieldError::field))
                .toList();
        return response(request, HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Validation failed",
                "One or more fields are invalid.", false, fields, List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiProblem> constraintViolation(HttpServletRequest request) {
        return response(request, HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Validation failed",
                "One or more fields are invalid.", false, List.of(), List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiProblem> malformed(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request",
                "The request body is malformed.", false, List.of(), List.of());
    }

    @ExceptionHandler(PersonalDataProtectionException.class)
    ResponseEntity<ApiProblem> protectedDataUnavailable(HttpServletRequest request) {
        return response(request, HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                "Service temporarily unavailable", "A required service is temporarily unavailable.",
                true, List.of(), List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiProblem> unexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected request failure requestId={} code=INTERNAL_ERROR", requestId(request));
        return response(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal error",
                "The request could not be completed.", false, List.of(), List.of());
    }

    private ResponseEntity<ApiProblem> response(
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String title,
            String detail,
            boolean retryable,
            List<ApiFieldError> fieldErrors,
            List<?> devices) {
        return ResponseEntity.status(status)
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problems.create(request, status.value(), code, title, detail,
                        retryable, fieldErrors, devices));
    }

    private static String validationCode(String code) {
        return switch (code == null ? "" : code) {
            case "NotNull", "NotBlank" -> "REQUIRED";
            case "Size" -> "INVALID_LENGTH";
            default -> "INVALID_VALUE";
        };
    }

    private static String safeValidationMessage(String code) {
        return switch (validationCode(code)) {
            case "REQUIRED" -> "This field is required.";
            case "INVALID_LENGTH" -> "This field has an invalid length.";
            default -> "This field is invalid.";
        };
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestContextFilter.REQUEST_ID_ATTRIBUTE);
        return value instanceof String requestId ? requestId : "unavailable";
    }
}
