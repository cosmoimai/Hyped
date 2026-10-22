package com.hyped.app.account.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AccountStore {
    record OwnedRoom(UUID roomId, String title) { }
    record Readiness(boolean ready, List<OwnedRoom> ownedRooms, int joinedRoomCount) { }
    record Job(UUID userId, UUID claim, String keyReference, byte[] firebaseUid, boolean firebaseDeleted) {
        @Override
        public String toString() { return "AccountDeletionJob[REDACTED]"; }
    }
    Readiness readiness(UUID userId);
    boolean activeSession(UUID userId, UUID sessionId, UUID installationId, Instant now);
    int activeDevices(UUID userId, Instant now);
    int activeRooms(UUID userId, boolean owned);
    String keyReference(UUID userId);
    void begin(UUID userId, String keyReference, byte[] firebaseUid, Instant now);
    List<UUID> due(Instant now);
    Job claim(UUID userId, Instant now);
    boolean firebaseDeleted(Job job);
    void finish(Job job, Instant now);
    void retry(Job job, Instant now);
}
