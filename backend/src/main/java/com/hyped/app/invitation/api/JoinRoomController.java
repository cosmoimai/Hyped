package com.hyped.app.invitation.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.membership.application.JoinRoomService;
import com.hyped.app.room.api.RoomController.RoomResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "hyped.invitation", name = "enabled", havingValue = "true")
public class JoinRoomController {
    private final JoinRoomService service;

    public JoinRoomController(JoinRoomService service) {
        this.service = service;
    }

    @PostMapping("/invitations/join")
    public ResponseEntity<RoomResponse> link(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody JoinRequest request) {
        return join(jwt, key, request, false);
    }

    @PostMapping("/room-codes/join")
    public ResponseEntity<RoomResponse> code(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody JoinRequest request) {
        return join(jwt, key, request, true);
    }

    private ResponseEntity<RoomResponse> join(Jwt jwt, String key, JoinRequest request, boolean roomCode) {
        var result = service.joinByPreviewReference(new UserId(UUID.fromString(jwt.getSubject())),
                request.previewReference(), roomCode, key);
        return ResponseEntity.status(result.created() ? 201 : 200)
                .body(RoomResponse.from(result.room(), result.serverNow()));
    }

    public record JoinRequest(String previewReference) {
        @JsonAnySetter
        public void unknown(String name, Object value) {
            throw new IllegalArgumentException("Unknown join field");
        }

        @Override
        public String toString() {
            return "JoinRequest[REDACTED]";
        }
    }
}
