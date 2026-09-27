package com.hyped.app.invitation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.invitation.application.port.out.InvitationCryptography;
import com.hyped.app.invitation.application.port.out.InvitationRepository;
import com.hyped.app.room.domain.RoomId;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Caller holds its account lock through lookup, mutation and remember; terminal results live for 24 hours. */
@Service
@ConditionalOnProperty(prefix = "hyped.invitation", name = "enabled", havingValue = "true")
public class InvitationReplayService {
    private final InvitationRepository invitations;
    private final InvitationCryptography crypto;
    private final ObjectMapper mapper;
    private final Clock clock;

    public InvitationReplayService(
            InvitationRepository invitations, InvitationCryptography crypto, ObjectMapper mapper, Clock clock) {
        this.invitations = invitations;
        this.crypto = crypto;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** A nonblocking transaction advisory lock precedes row locks; collisions only cause a safe retry. */
    public void claim(UserId actor, String operation, String key) {
        key(key);
        long lock = ByteBuffer.wrap(crypto.digest("command-lock", actor.value() + ":" + operation + ":" + key))
                .getLong();
        if (!invitations.claim(lock)) {
            throw new InvitationException(409, "REQUEST_IN_PROGRESS");
        }
    }

    public void reject(UserId actor, String operation, String key, String request, RoomId resource,
            InvitationException failure) {
        remember(actor, operation, key, request, Map.of("errorCode", failure.code()), resource, failure.status());
        throw new RecordedInvitationException(failure);
    }

    public <T> Optional<T> find(UserId actor, String operation, String key, String request, Class<T> type) {
        byte[] digest = key(key);
        return invitations.replay(actor, operation, key, clock.instant()).map(replay -> {
            if (!MessageDigest.isEqual(replay.requestDigest(), crypto.digest("request", request))) {
                throw new InvitationException(409, "IDEMPOTENCY_KEY_REUSED");
            }
            try {
                String response = crypto.decrypt(context(actor, operation, digest), replay.responseCiphertext());
                if (replay.status() >= 400) {
                    throw new InvitationException(replay.status(), mapper.readTree(response).get("errorCode").asText());
                }
                return mapper.readValue(response, type);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Invitation replay unavailable");
            }
        });
    }

    public void remember(UserId actor, String operation, String key, String request, Object response,
            RoomId resource, int status) {
        byte[] digest = key(key);
        try {
            invitations.remember(actor, operation, key, crypto.digest("request", request),
                    crypto.encrypt(context(actor, operation, digest), mapper.writeValueAsString(response)),
                    resource, status, clock.instant(), clock.instant().plusSeconds(86_400));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invitation replay unavailable");
        }
    }

    private byte[] key(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new InvitationException(422, "VALIDATION_FAILED");
        }
        return crypto.digest("idempotency", value);
    }

    private String context(UserId actor, String operation, byte[] key) {
        return "replay:" + actor.value() + ":" + operation + ":" + HexFormat.of().formatHex(key);
    }
}
