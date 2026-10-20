package com.hyped.app.reminder.api;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.reminder.application.ReminderPreferencesService;
import com.hyped.app.reminder.domain.ReminderPreferences;
import com.hyped.app.room.domain.RoomId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/reminder-preferences")
@ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "true")
public class ReminderPreferencesController {
    private final ReminderPreferencesService service;

    public ReminderPreferencesController(ReminderPreferencesService service) {
        this.service = service;
    }

    @GetMapping
    public ReminderPreferencesResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID roomId) {
        return ReminderPreferencesResponse.from(service.get(actor(jwt), new RoomId(roomId)));
    }

    @PutMapping
    public ReminderPreferencesResponse replace(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID roomId,
            @Valid @RequestBody ReplaceReminderPreferencesRequest request) {
        return ReminderPreferencesResponse.from(service.replace(actor(jwt), new RoomId(roomId),
                request.remind24HoursBefore(), request.remind1HourBefore(), request.remindAtEventTime()));
    }

    private static UserId actor(Jwt jwt) {
        return new UserId(UUID.fromString(jwt.getSubject()));
    }

    public record ReplaceReminderPreferencesRequest(
            @NotNull Boolean remind24HoursBefore,
            @NotNull Boolean remind1HourBefore,
            @NotNull Boolean remindAtEventTime) {
    }

    public record ReminderPreferencesResponse(
            boolean remind24HoursBefore,
            boolean remind1HourBefore,
            boolean remindAtEventTime,
            java.time.Instant updatedAt) {
        static ReminderPreferencesResponse from(ReminderPreferences value) {
            return new ReminderPreferencesResponse(value.remind24HoursBefore(), value.remind1HourBefore(),
                    value.remindAtEventTime(), value.updatedAt());
        }
    }
}
