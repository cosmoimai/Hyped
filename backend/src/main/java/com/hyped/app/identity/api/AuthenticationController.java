package com.hyped.app.identity.api;

import com.hyped.app.common.api.ApiProblemException;
import com.hyped.app.identity.application.model.AuthenticationExchangeCommand;
import com.hyped.app.identity.application.model.AuthenticationExchangeResult;
import com.hyped.app.identity.application.model.AuthenticationUserProfile;
import com.hyped.app.identity.application.model.DeviceSummary;
import com.hyped.app.identity.application.model.RefreshSessionResult;
import com.hyped.app.identity.application.model.SessionTokenPair;
import com.hyped.app.identity.application.service.AuthenticationExchangeService;
import com.hyped.app.identity.application.service.AuthenticationProfileReader;
import com.hyped.app.identity.application.service.LogoutSessionService;
import com.hyped.app.identity.application.service.RefreshSessionService;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.DevicePlatform;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.SessionId;
import com.hyped.app.identity.domain.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "true")
public class AuthenticationController {
    private final AuthenticationExchangeService exchangeService;
    private final RefreshSessionService refreshService;
    private final LogoutSessionService logoutService;
    private final AuthenticationProfileReader profiles;

    public AuthenticationController(
            AuthenticationExchangeService exchangeService,
            RefreshSessionService refreshService,
            LogoutSessionService logoutService,
            AuthenticationProfileReader profiles) {
        this.exchangeService = exchangeService;
        this.refreshService = refreshService;
        this.logoutService = logoutService;
        this.profiles = profiles;
    }

    @PostMapping("/exchange")
    public ResponseEntity<ExchangeResponse> exchange(@Valid @RequestBody ExchangeRequest request) {
        AuthenticationExchangeResult result = exchangeService.exchange(new AuthenticationExchangeCommand(
                request.firebaseIdToken(), new InstallationId(request.installationId()), request.platform(),
                request.deviceName(), request.appVersion()));
        if (result instanceof AuthenticationExchangeResult.Success success) {
            AuthenticationUserProfile profile = profiles.read(success.userId());
            HttpStatus status = success.newAccount() ? HttpStatus.CREATED : HttpStatus.OK;
            return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                    .body(ExchangeResponse.from(success.tokens(), profile, success.newAccount()));
        }
        if (result instanceof AuthenticationExchangeResult.DeviceLimitReached limit) {
            throw deviceLimit(limit.devices());
        }
        if (result instanceof AuthenticationExchangeResult.AccountLinkConfirmationRequired) {
            throw problem(HttpStatus.CONFLICT, "ACCOUNT_LINK_CONFIRMATION_REQUIRED",
                    "Account linking confirmation required",
                    "Another account uses this verified email. Confirm account linking to continue.");
        }
        AuthenticationExchangeResult.AccountUnavailable unavailable =
                (AuthenticationExchangeResult.AccountUnavailable) result;
        throw accountUnavailable(unavailable.status());
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshSessionResult result = refreshService.refresh(
                request.refreshToken(), new InstallationId(request.installationId()));
        if (result instanceof RefreshSessionResult.Success success) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(TokenPairResponse.from(success.tokens()));
        }
        String code = ((RefreshSessionResult.ReauthenticationRequired) result).code();
        throw refreshFailure(code);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        logoutService.logout(new UserId(UUID.fromString(jwt.getSubject())),
                new SessionId(UUID.fromString(jwt.getClaimAsString("sid"))));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static ApiProblemException deviceLimit(List<DeviceSummary> devices) {
        List<DeviceLimitItem> safeDevices = devices.stream().map(DeviceLimitItem::from).toList();
        return new ApiProblemException(HttpStatus.CONFLICT, "DEVICE_LIMIT_REACHED", "Device limit reached",
                "This account already has five active devices.", false, safeDevices);
    }

    private static ApiProblemException accountUnavailable(AccountStatus status) {
        return switch (status) {
            case DELETED -> problem(HttpStatus.GONE, "ACCOUNT_DELETED", "Account deleted",
                    "This account has been deleted.");
            case SUSPENDED -> problem(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "Account suspended",
                    "This account is unavailable.");
            case COMPROMISED -> problem(HttpStatus.FORBIDDEN, "ACCOUNT_COMPROMISED", "Account unavailable",
                    "This account is unavailable.");
            case DELETION_PENDING -> problem(HttpStatus.FORBIDDEN, "ACCOUNT_DELETION_PENDING",
                    "Account deletion pending", "This account is unavailable.");
            case LOCKED -> problem(HttpStatus.UNAUTHORIZED, "ACCOUNT_LOCKED", "Account temporarily locked",
                    "This account is temporarily locked.");
            case ACTIVE -> throw new IllegalArgumentException("An active account cannot be unavailable");
        };
    }

    private static ApiProblemException refreshFailure(String code) {
        return switch (code) {
            case "REFRESH_TOKEN_REUSE_DETECTED" -> problem(HttpStatus.UNAUTHORIZED, code,
                    "Refresh token reuse detected", "Sign in again on this device.");
            case "SESSION_DEVICE_MISMATCH" -> problem(HttpStatus.UNAUTHORIZED, code,
                    "Session device mismatch", "The refresh token does not belong to this installation.");
            case "ACCOUNT_UNAVAILABLE" -> problem(HttpStatus.FORBIDDEN, code,
                    "Account unavailable", "This account is unavailable.");
            default -> problem(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Session expired",
                    "The session is invalid or expired.");
        };
    }

    private static ApiProblemException problem(
            HttpStatus status, String code, String title, String detail) {
        return new ApiProblemException(status, code, title, detail);
    }

    public record ExchangeRequest(
            @NotBlank @Size(max = 16384) String firebaseIdToken,
            @NotNull UUID installationId,
            @NotNull DevicePlatform platform,
            @NotBlank @Size(max = 80) String deviceName,
            @NotBlank @Size(max = 64) String appVersion) {

        @Override
        public String toString() {
            return "ExchangeRequest[firebaseIdToken=[REDACTED], installationId=" + installationId
                    + ", platform=" + platform + ", deviceName=" + deviceName
                    + ", appVersion=" + appVersion + "]";
        }
    }

    public record RefreshRequest(
            @NotBlank @Size(max = 512) String refreshToken,
            @NotNull UUID installationId) {

        @Override
        public String toString() {
            return "RefreshRequest[refreshToken=[REDACTED], installationId=" + installationId + "]";
        }
    }

    public record TokenPairResponse(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            UUID sessionId) {

        static TokenPairResponse from(SessionTokenPair pair) {
            return new TokenPairResponse(pair.accessToken().tokenValue(), pair.accessToken().expiresAt(),
                    pair.refreshToken().tokenValue(), pair.refreshToken().expiresAt(), pair.sessionId().value());
        }

        @Override
        public String toString() {
            return "TokenPairResponse[tokens=[REDACTED], accessTokenExpiresAt=" + accessTokenExpiresAt
                    + ", refreshTokenExpiresAt=" + refreshTokenExpiresAt + ", sessionId=" + sessionId + "]";
        }
    }

    public record ExchangeResponse(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            UUID sessionId,
            UserResponse user,
            boolean isNewAccount) {

        static ExchangeResponse from(
                SessionTokenPair pair, AuthenticationUserProfile profile, boolean newAccount) {
            TokenPairResponse tokens = TokenPairResponse.from(pair);
            return new ExchangeResponse(tokens.accessToken(), tokens.accessTokenExpiresAt(),
                    tokens.refreshToken(), tokens.refreshTokenExpiresAt(), tokens.sessionId(),
                    UserResponse.from(profile), newAccount);
        }

        @Override
        public String toString() {
            return "ExchangeResponse[tokens=[REDACTED], sessionId=" + sessionId
                    + ", user=[REDACTED], isNewAccount=" + isNewAccount + "]";
        }
    }

    public record UserResponse(UUID id, String displayName, PhotoResponse photo, long profileRevision) {
        static UserResponse from(AuthenticationUserProfile profile) {
            PhotoResponse photo = profile.photoMediaId() == null && profile.providerPhotoUrl() == null
                    ? null : new PhotoResponse(profile.photoMediaId(), profile.providerPhotoUrl());
            return new UserResponse(profile.id().value(), profile.displayName(), photo, profile.profileRevision());
        }
    }

    public record PhotoResponse(UUID mediaAssetId, String url) {
    }

    public record DeviceLimitItem(UUID deviceId, String deviceName, DevicePlatform platform, Instant lastActiveAt) {
        static DeviceLimitItem from(DeviceSummary device) {
            return new DeviceLimitItem(device.id().value(), device.name(), device.platform(), device.lastActiveAt());
        }
    }
}
