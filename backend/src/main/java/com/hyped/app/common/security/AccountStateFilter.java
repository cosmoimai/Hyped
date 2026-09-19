package com.hyped.app.common.security;

import com.hyped.app.common.api.ApiProblemWriter;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.UserAccount;
import com.hyped.app.identity.domain.UserId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccountStateFilter extends OncePerRequestFilter {
    private final UserAccountRepository accounts;
    private final ApiProblemWriter problems;

    public AccountStateFilter(UserAccountRepository accounts, ApiProblemWriter problems) {
        this.accounts = accounts;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }
        Jwt jwt = token.getToken();
        UserAccount account;
        try {
            account = accounts.findById(new UserId(UUID.fromString(jwt.getSubject()))).orElse(null);
        } catch (RuntimeException exception) {
            problems.write(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DEPENDENCY_UNAVAILABLE", "Service temporarily unavailable",
                    "A required service is temporarily unavailable.");
            return;
        }
        if (account == null) {
            problems.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "ACCESS_TOKEN_INVALID", "Access token invalid", "The access token is invalid or expired.");
            return;
        }
        if (denied(account.status())) {
            writeUnavailable(request, response, account.status());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnavailable(
            HttpServletRequest request, HttpServletResponse response, AccountStatus status) throws IOException {
        String code = switch (status) {
            case SUSPENDED -> "ACCOUNT_SUSPENDED";
            case COMPROMISED -> "ACCOUNT_COMPROMISED";
            case DELETION_PENDING -> "ACCOUNT_DELETION_PENDING";
            case DELETED -> "ACCOUNT_DELETED";
            default -> throw new IllegalArgumentException("Account status is available");
        };
        problems.write(request, response, HttpServletResponse.SC_FORBIDDEN,
                code, "Account unavailable", "This account is unavailable.");
    }

    private static boolean denied(AccountStatus status) {
        return status == AccountStatus.SUSPENDED
                || status == AccountStatus.COMPROMISED
                || status == AccountStatus.DELETION_PENDING
                || status == AccountStatus.DELETED;
    }
}
