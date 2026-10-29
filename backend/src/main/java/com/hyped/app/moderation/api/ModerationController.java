package com.hyped.app.moderation.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.hyped.app.moderation.application.ModerationService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/moderation/reports")
@ConditionalOnProperty(name = "hyped.moderation.review-enabled", havingValue = "true")
public class ModerationController {
    private final ModerationService service;

    public ModerationController(ModerationService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, List<ModerationService.QueueItem>> queue(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "50") int limit) {
        service.requireRate(jwt.getSubject());
        return Map.of("items", service.queue(limit));
    }

    @GetMapping("/{id}")
    public ModerationService.Context context(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.requireRate(jwt.getSubject());
        return service.context(id);
    }

    @GetMapping(value = "/{id}/photo", produces = "image/jpeg")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.requireRate(jwt.getSubject());
        return ResponseEntity.ok()
                .header("Cache-Control", "private, no-store")
                .body(service.photo(id));
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<Void> decide(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody Decision body) {
        service.requireRate(jwt.getSubject());
        service.decide(id, body.action(), body.expectedRoomRevision(), jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    public record Decision(String action, Long expectedRoomRevision) {
        @JsonAnySetter
        public void unknown(String name, Object value) {
            throw new IllegalArgumentException();
        }
    }
}
