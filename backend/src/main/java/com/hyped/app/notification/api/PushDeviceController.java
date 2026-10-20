package com.hyped.app.notification.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.hyped.app.common.api.ApiProblemException;
import com.hyped.app.notification.application.PushRegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices/{installationId}")
@ConditionalOnProperty(prefix = "hyped.push", name = "enabled", havingValue = "true")
public class PushDeviceController {
    private final PushRegistrationService service;

    public PushDeviceController(PushRegistrationService service) {
        this.service = service;
    }

    @PutMapping
    public ResponseEntity<Void> put(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID installationId,
            @Valid @RequestBody Registration request) {
        checkInstallation(jwt, installationId);
        service.register(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("sid")),
                installationId, request.fcmToken(), request.notificationsEnabled());
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID installationId) {
        checkInstallation(jwt, installationId);
        service.register(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("sid")),
                installationId, null, false);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    private static void checkInstallation(Jwt jwt, UUID installation) {
        if (!installation.toString().equals(jwt.getClaimAsString("did"))) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "DEVICE_UNAVAILABLE",
                    "Device unavailable", "Use the current authenticated installation.");
        }
    }

    public record Registration(@Pattern(regexp = "ANDROID") @NotBlank String platform,
            String fcmToken, @NotNull Boolean notificationsEnabled,
            @NotBlank @Size(max = 64) String appVersion) {
        @AssertTrue(message = "Provide a valid token when notifications are enabled")
        public boolean isValidToken() {
            return !Boolean.TRUE.equals(notificationsEnabled) || fcmToken != null && !fcmToken.isBlank()
                    && fcmToken.length() <= 4096 && fcmToken.chars().noneMatch(Character::isWhitespace);
        }

        @JsonAnySetter
        public void unknown(String name, Object value) {
            throw new IllegalArgumentException("Unknown push registration field");
        }

        @Override
        public String toString() {
            return "PushRegistration[REDACTED]";
        }
    }
}
