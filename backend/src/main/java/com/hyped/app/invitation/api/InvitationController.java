package com.hyped.app.invitation.api;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.invitation.application.InvitationService.Share;
import com.hyped.app.invitation.application.InvitationService;
import com.hyped.app.room.domain.RoomId;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/invitation")
@ConditionalOnProperty(prefix = "hyped.invitation", name = "enabled", havingValue = "true")
public class InvitationController {
    private final InvitationService service;

    public InvitationController(InvitationService service) {
        this.service = service;
    }

    @GetMapping
    public Share get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID roomId) {
        return service.get(new UserId(UUID.fromString(jwt.getSubject())), new RoomId(roomId));
    }

    @PostMapping("/rotate")
    public Share rotate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID roomId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.rotate(new UserId(UUID.fromString(jwt.getSubject())), new RoomId(roomId), key);
    }
}
