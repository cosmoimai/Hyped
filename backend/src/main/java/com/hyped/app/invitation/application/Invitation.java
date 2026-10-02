package com.hyped.app.invitation.application;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.room.domain.RoomId;
import java.time.Instant;

public record Invitation(
        RoomId roomId, int generation, byte[] linkTokenHash, byte[] roomCodeHmac,
        byte[] linkTokenCiphertext, byte[] roomCodeCiphertext, UserId createdByUserId,
        Instant createdAt, Instant updatedAt) {
    public Invitation {
        linkTokenHash = linkTokenHash.clone();
        roomCodeHmac = roomCodeHmac.clone();
        linkTokenCiphertext = linkTokenCiphertext.clone();
        roomCodeCiphertext = roomCodeCiphertext.clone();
    }

    @Override
    public byte[] linkTokenHash() {
        return linkTokenHash.clone();
    }

    @Override
    public byte[] roomCodeHmac() {
        return roomCodeHmac.clone();
    }

    @Override
    public byte[] linkTokenCiphertext() {
        return linkTokenCiphertext.clone();
    }

    @Override
    public byte[] roomCodeCiphertext() {
        return roomCodeCiphertext.clone();
    }

    @Override
    public String toString() {
        return "Invitation[REDACTED]";
    }
}
