package com.hyped.app.room.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.hyped.app.common.api.ApiProblemException;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.room.application.RoomService;
import com.hyped.app.room.application.RoomService.CreateRoomCommand;
import com.hyped.app.room.application.RoomService.OwnershipTransfer;
import com.hyped.app.room.application.RoomService.UpdateRoomCommand;
import com.hyped.app.room.application.port.out.RoomRepository.AuthorizedRoom;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.Room;
import com.hyped.app.room.domain.RoomId;
import com.hyped.app.room.domain.RoomMembership;
import com.hyped.app.room.domain.RoomStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
@ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "true")
public class RoomController {
    private final RoomService service;
    private final Clock clock;

    public RoomController(RoomService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateRoomRequest request) {
        AuthorizedRoom created = service.create(actor(jwt), new CreateRoomCommand(request.title(),
                request.eventLocalDate(), request.eventLocalTime(), request.eventTimeZone(),
                request.location(), request.description()));
        RoomResponse response = RoomResponse.from(created, clock.instant());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, "/api/v1/rooms/" + response.id())
                .eTag(etag(response.revision()))
                .body(response);
    }

    @GetMapping
    public RoomListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) RoomStatus status,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        List<RoomResponse> items = service.list(actor(jwt), status, limit).stream()
                .map(room -> RoomResponse.from(room, clock.instant())).toList();
        return new RoomListResponse(items, null);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID roomId) {
        RoomResponse response = RoomResponse.from(service.get(actor(jwt), new RoomId(roomId)), clock.instant());
        return ResponseEntity.ok().eTag(etag(response.revision())).body(response);
    }

    @PatchMapping(value = "/{roomId}", consumes = "application/merge-patch+json")
    public ResponseEntity<RoomResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody JsonNode patch) {
        rejectUnknown(patch, List.of("title", "eventLocalDate", "eventLocalTime", "eventTimeZone",
                "location", "description"));
        rejectClearedRequired(patch, List.of("title", "eventLocalDate", "eventLocalTime", "eventTimeZone"));
        UpdateRoomCommand command = new UpdateRoomCommand(text(patch, "title"), date(patch, "eventLocalDate"),
                time(patch, "eventLocalTime"), text(patch, "eventTimeZone"), text(patch, "location"),
                patch.has("location"), text(patch, "description"), patch.has("description"));
        AuthorizedRoom updated = service.update(actor(jwt), new RoomId(roomId), revision(ifMatch), command);
        RoomResponse response = RoomResponse.from(updated, clock.instant());
        return ResponseEntity.ok().eTag(etag(response.revision())).body(response);
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<ArchiveResponse> archive(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID roomId) {
        Room room = service.archive(actor(jwt), new RoomId(roomId)).room();
        return ResponseEntity.accepted().body(new ArchiveResponse(room.id().value(), room.status(),
                room.archivedAt(), room.deleteAfter()));
    }

    @PatchMapping("/{roomId}/members/{userId}")
    public MembershipResponse role(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @PathVariable UUID userId,
            @Valid @RequestBody RoleRequest request) {
        return MembershipResponse.from(service.changeRole(actor(jwt), new RoomId(roomId),
                new UserId(userId), request.role()));
    }

    @PostMapping("/{roomId}/ownership-transfer")
    public OwnershipTransferResponse transfer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @Valid @RequestBody OwnershipTransferRequest request) {
        OwnershipTransfer result = service.transfer(actor(jwt), new RoomId(roomId),
                new UserId(request.newOwnerUserId()));
        return new OwnershipTransferResponse(RoomResponse.from(result.room(), clock.instant()),
                MembershipResponse.from(result.previousOwner()), MembershipResponse.from(result.newOwner()));
    }

    private static UserId actor(Jwt jwt) {
        return new UserId(UUID.fromString(jwt.getSubject()));
    }

    private static long revision(String value) {
        if (value == null || !value.matches("\"room-[1-9][0-9]*\"")) {
            throw new ApiProblemException(HttpStatus.PRECONDITION_REQUIRED, "ROOM_REVISION_REQUIRED",
                    "Room revision required", "Provide the current room ETag in If-Match.");
        }
        try {
            return Long.parseLong(value.substring(6, value.length() - 1));
        } catch (NumberFormatException exception) {
            throw new ApiProblemException(HttpStatus.PRECONDITION_REQUIRED, "ROOM_REVISION_REQUIRED",
                    "Room revision required", "Provide the current room ETag in If-Match.");
        }
    }

    private static String etag(long revision) {
        return "\"room-" + revision + "\"";
    }

    private static String text(JsonNode patch, String field) {
        JsonNode value = patch.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw malformedPatch();
        }
        return value.textValue();
    }

    private static LocalDate date(JsonNode patch, String field) {
        String value = text(patch, field);
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw malformedPatch();
        }
    }

    private static LocalTime time(JsonNode patch, String field) {
        String value = text(patch, field);
        try {
            return value == null ? null : LocalTime.parse(value);
        } catch (RuntimeException exception) {
            throw malformedPatch();
        }
    }

    private static void rejectUnknown(JsonNode patch, List<String> allowed) {
        if (!patch.isObject() || patch.isEmpty()
                || patch.properties().stream().anyMatch(entry -> !allowed.contains(entry.getKey()))) {
            throw malformedPatch();
        }
    }

    private static void rejectClearedRequired(JsonNode patch, List<String> fields) {
        if (fields.stream().anyMatch(field -> patch.has(field) && patch.get(field).isNull())) {
            throw malformedPatch();
        }
    }

    private static ApiProblemException malformedPatch() {
        return new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Validation failed",
                "The room patch is invalid.");
    }

    public record CreateRoomRequest(
            @NotBlank @Size(max = 80) String title,
            @NotNull LocalDate eventLocalDate,
            @NotNull LocalTime eventLocalTime,
            @NotBlank @Size(max = 64) String eventTimeZone,
            @Size(max = 120) String location,
            @Size(max = 500) String description) {
    }

    public record RoleRequest(@NotNull MembershipRole role) {
    }

    public record OwnershipTransferRequest(@NotNull UUID newOwnerUserId) {
    }

    public record RoomListResponse(List<RoomResponse> items, String nextCursor) {
        public RoomListResponse {
            items = List.copyOf(items);
        }
    }

    public record RoomResponse(
            UUID id,
            String title,
            Instant eventAt,
            String eventTimeZone,
            String location,
            String description,
            RoomStatus status,
            MembershipRole role,
            int memberCount,
            long revision,
            Instant archivedAt,
            Instant deleteAfter,
            Instant createdAt,
            Instant updatedAt,
            Instant serverNow) {
        static RoomResponse from(AuthorizedRoom authorized, Instant serverNow) {
            Room room = authorized.room();
            return new RoomResponse(room.id().value(), room.title(), room.eventAt(), room.eventTimeZone(),
                    room.location(), room.description(), room.status(), authorized.role(), room.memberCount(),
                    room.revision(), room.archivedAt(), room.deleteAfter(), room.createdAt(), room.updatedAt(),
                    serverNow);
        }
    }

    public record ArchiveResponse(UUID roomId, RoomStatus status, Instant archivedAt, Instant deleteAfter) {
    }

    public record MembershipResponse(UUID userId, MembershipRole role, Instant joinedAt, Instant updatedAt) {
        static MembershipResponse from(RoomMembership membership) {
            return new MembershipResponse(membership.userId().value(), membership.role(), membership.joinedAt(),
                    membership.updatedAt());
        }
    }

    public record OwnershipTransferResponse(
            RoomResponse room,
            MembershipResponse previousOwner,
            MembershipResponse newOwner) {
    }
}
