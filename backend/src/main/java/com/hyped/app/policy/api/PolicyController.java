package com.hyped.app.policy.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.hyped.app.account.application.AccountService;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.policy.application.PolicyCatalog;
import com.hyped.app.policy.application.PolicyService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "true")
public class PolicyController {
    private final PolicyCatalog catalog;
    private final PolicyService service;
    private final AccountService accounts;
    public PolicyController(PolicyCatalog catalog, PolicyService service, AccountService accounts) {
        this.catalog = catalog;
        this.service = service;
        this.accounts = accounts;
    }
    public record PublicDocuments(PolicyCatalog.Versions required, PolicyCatalog.Documents documents,
            boolean documentsAvailable) { }
    @GetMapping("/api/v1/public/policies")
    public ResponseEntity<PublicDocuments> documents() {
        return noStore(new PublicDocuments(catalog.required(), catalog.documents(), catalog.available()));
    }
    @GetMapping("/api/v1/public/legal/{kind}/{version}")
    public ResponseEntity<PolicyCatalog.Document> document(@PathVariable String kind, @PathVariable String version) {
        return noStore(catalog.document(kind, version));
    }
    @GetMapping("/api/v1/me/policy-status")
    public ResponseEntity<PolicyService.Status> status(@AuthenticationPrincipal Jwt jwt) {
        return noStore(service.status(actor(jwt)));
    }
    @PostMapping("/api/v1/me/policy-acceptances")
    public ResponseEntity<PolicyService.Status> accept(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody Request body) {
        return noStore(service.accept(actor(jwt), new PolicyCatalog.Versions(body.termsVersion(), body.privacyVersion(),
                body.contentRulesVersion()), body.accepted(), body.privacyAccepted(), body.adultAffirmed()));
    }
    private UserId actor(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        accounts.requireSession(id, UUID.fromString(jwt.getClaimAsString("sid")),
                UUID.fromString(jwt.getClaimAsString("did")));
        return new UserId(id);
    }
    private static <T> ResponseEntity<T> noStore(T value) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(value);
    }
    public record Request(@NotBlank @Size(max = 32) String termsVersion,
            @NotBlank @Size(max = 32) String privacyVersion, @NotBlank @Size(max = 32) String contentRulesVersion,
            boolean accepted, boolean privacyAccepted, boolean adultAffirmed) {
        @JsonAnySetter
        public void unknown(String name, Object value) { throw new IllegalArgumentException("Unknown consent field"); }
    }
}
