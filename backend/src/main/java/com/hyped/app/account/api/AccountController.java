package com.hyped.app.account.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.hyped.app.account.application.AccountService;
import com.hyped.app.account.application.port.out.AccountStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "true")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }
    private UUID actor(Jwt jwt) {
        UUID actor = UUID.fromString(jwt.getSubject());
        service.requireSession(actor, UUID.fromString(jwt.getClaimAsString("sid")),
                UUID.fromString(jwt.getClaimAsString("did")));
        return actor;
    }
    @GetMapping
    public ResponseEntity<AccountService.Profile> profile(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(service.profile(actor(jwt)));
    }
    @GetMapping("/deletion-readiness")
    public ResponseEntity<AccountStore.Readiness> readiness(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(service.readiness(actor(jwt)));
    }
    @PostMapping("/deletion-requests")
    public ResponseEntity<AccountService.Accepted> delete(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody Deletion request) {
        return ResponseEntity.accepted().header("Cache-Control", "private, no-store")
                .body(service.request(actor(jwt), request.recentFirebaseIdToken()));
    }
    public record Deletion(String recentFirebaseIdToken) {
        @jakarta.validation.constraints.AssertTrue(message = "Provide a valid recent authentication proof")
        public boolean isValidProof() {
            return recentFirebaseIdToken != null && !recentFirebaseIdToken.isBlank()
                    && recentFirebaseIdToken.length() <= 16384;
        }
        @JsonAnySetter
        public void unknown(String name, Object value) { throw new IllegalArgumentException("Unknown deletion field"); }
        @Override
        public String toString() { return "AccountDeletion[REDACTED]"; }
    }
}
