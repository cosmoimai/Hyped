package com.hyped.app.moderation.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.moderation.application.SafetyService;
import com.hyped.app.moderation.application.port.out.SafetyStore;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "hyped.tokens.enabled", havingValue = "true")
public class SafetyController {
    private final SafetyService service;

    public SafetyController(SafetyService service) {
        this.service = service;
    }

    @PostMapping("/reports")
    public ResponseEntity<SafetyService.Receipt> report(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody ReportBody body) {
        return ResponseEntity.status(201)
                .header("Cache-Control", "private, no-store")
                .body(
                        service.report(
                                actor(jwt),
                                key,
                                new SafetyService.ReportRequest(
                                        body.roomId(),
                                        body.subjectType(),
                                        body.subjectUserId(),
                                        body.reasonCode(),
                                        body.details())));
    }

    @PutMapping("/me/blocks/{target}")
    public SafetyService.BlockResult block(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID target,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody BlockBody body) {
        return service.block(actor(jwt), target, body.roomId(), key);
    }

    @DeleteMapping("/me/blocks/{target}")
    public ResponseEntity<Void> unblock(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID target) {
        service.unblock(actor(jwt), target);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/blocks")
    public SafetyService.BlockPage blocks(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) UUID after) {
        return service.blocks(actor(jwt), after);
    }

    @GetMapping("/me/room-invalidations")
    public Map<String, List<SafetyStore.Invalidation>> invalidations(
            @AuthenticationPrincipal Jwt jwt) {
        return Map.of("items", service.invalidations(actor(jwt)));
    }

    @DeleteMapping("/me/room-invalidations/{version}")
    public ResponseEntity<Void> acknowledge(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID version) {
        service.acknowledge(actor(jwt), version);
        return ResponseEntity.noContent().build();
    }

    private static UserId actor(Jwt jwt) {
        return new UserId(UUID.fromString(jwt.getSubject()));
    }

    public record BlockBody(UUID roomId) {
        @JsonAnySetter
        public void unknown(String name, Object value) {
            throw new IllegalArgumentException();
        }
    }

    public record ReportBody(
            UUID roomId,
            String subjectType,
            UUID subjectUserId,
            String reasonCode,
            String details) {
        @JsonAnySetter
        public void unknown(String name, Object value) {
            throw new IllegalArgumentException();
        }

        @Override
        public String toString() {
            return "ReportBody[REDACTED]";
        }
    }
}
