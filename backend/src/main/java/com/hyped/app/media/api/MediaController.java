package com.hyped.app.media.api;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.media.application.MediaService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@ConditionalOnProperty(name = "hyped.tokens.enabled", havingValue = "true")
public class MediaController {
    private final MediaService service;

    public MediaController(MediaService service) {
        this.service = service;
    }

    @GetMapping("/configuration")
    public Map<String, Boolean> configuration() {
        return Map.of("available", service.available());
    }

    @PostMapping("/uploads")
    public ResponseEntity<MediaService.Authorized> upload(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadRequest body) {
        return ResponseEntity.status(201)
                .header("Cache-Control", "private, no-store")
                .body(
                        service.authorize(
                                actor(jwt),
                                new MediaService.Request(
                                        body.scope(),
                                        body.fileName(),
                                        body.contentType(),
                                        body.sizeBytes(),
                                        body.widthPx(),
                                        body.heightPx(),
                                        body.contentSha256())));
    }

    @PostMapping("/uploads/{id}/complete")
    public ResponseEntity<MediaService.State> complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CompleteRequest body) {
        var result = service.complete(actor(jwt), id, body.contentSha256());
        return ResponseEntity.status(result.status().equals("READY") ? 200 : 202)
                .header("Cache-Control", "private, no-store")
                .body(result);
    }

    @GetMapping("/{id}")
    public MediaService.Delivery delivery(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.delivery(actor(jwt), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(actor(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static UserId actor(Jwt jwt) {
        return new UserId(UUID.fromString(jwt.getSubject()));
    }

    public record UploadRequest(
            @NotBlank @Size(max = 16) String scope,
            @Size(max = 128) String fileName,
            @NotBlank @Size(max = 32) String contentType,
            int sizeBytes,
            int widthPx,
            int heightPx,
            @NotBlank @Size(max = 44) String contentSha256) {
        @Override
        public String toString() {
            return "UploadRequest[REDACTED]";
        }
    }

    public record CompleteRequest(@NotBlank @Size(max = 44) String contentSha256) {
        @Override
        public String toString() {
            return "CompleteRequest[REDACTED]";
        }
    }
}
