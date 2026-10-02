package com.hyped.app.invitation.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.hyped.app.invitation.application.InvitationResolver.Preview;
import com.hyped.app.invitation.application.InvitationResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@ConditionalOnProperty(prefix = "hyped.invitation", name = "enabled", havingValue = "true")
public class PublicInvitationController {
    private final InvitationResolver resolver;

    public PublicInvitationController(InvitationResolver resolver) {
        this.resolver = resolver;
    }

    @PostMapping("/invitations/preview")
    public Preview link(@RequestBody LinkRequest request) {
        return resolver.preview(request.token(), false);
    }

    @PostMapping("/room-codes/preview")
    public Preview code(@RequestBody CodeRequest request) {
        return resolver.preview(request.roomCode(), true);
    }

    public record LinkRequest(String token) {
        @JsonAnySetter
        public void unknown(String name, Object value) {
            throw new IllegalArgumentException("Unknown invitation field");
        }

        @Override
        public String toString() {
            return "LinkRequest[REDACTED]";
        }
    }

    public record CodeRequest(String roomCode) {
        @JsonAnySetter
        public void unknown(String name, Object value) {
            throw new IllegalArgumentException("Unknown invitation field");
        }

        @Override
        public String toString() {
            return "CodeRequest[REDACTED]";
        }
    }
}
