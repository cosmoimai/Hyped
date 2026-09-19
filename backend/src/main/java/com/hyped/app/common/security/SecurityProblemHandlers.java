package com.hyped.app.common.security;

import com.hyped.app.common.api.ApiProblemWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ApiProblemWriter problems;

    public SecurityProblemHandlers(ApiProblemWriter problems) {
        this.problems = problems;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        boolean hasAuthorization = request.getHeader("Authorization") != null;
        String code = hasAuthorization ? "ACCESS_TOKEN_INVALID" : "AUTHENTICATION_REQUIRED";
        String title = hasAuthorization ? "Access token invalid" : "Authentication required";
        String detail = hasAuthorization
                ? "The access token is invalid or expired." : "A valid access token is required.";
        problems.write(request, response, HttpServletResponse.SC_UNAUTHORIZED, code, title, detail);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException, ServletException {
        problems.write(request, response, HttpServletResponse.SC_FORBIDDEN,
                "ACCESS_DENIED", "Access denied", "This action is not permitted.");
    }
}
