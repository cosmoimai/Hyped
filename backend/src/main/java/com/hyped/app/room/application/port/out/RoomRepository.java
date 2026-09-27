package com.hyped.app.room.application.port.out;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.Room;
import com.hyped.app.room.domain.RoomId;
import com.hyped.app.room.domain.RoomMembership;
import com.hyped.app.room.domain.RoomStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoomRepository {
    void lockUser(UserId userId);

    long countActiveOwned(UserId userId);

    long countActiveJoined(UserId userId);

    void create(Room room, RoomMembership owner);

    List<AuthorizedRoom> findByUser(UserId userId, RoomStatus status, int limit);

    Optional<AuthorizedRoom> findAuthorized(RoomId roomId, UserId userId);

    Optional<AuthorizedRoom> findAuthorizedForUpdate(RoomId roomId, UserId userId);

    Optional<Room> findForUpdate(RoomId roomId);

    Optional<RoomMembership> findMembershipForUpdate(RoomId roomId, UserId userId);

    boolean updateVisibleFields(Room room, long expectedRevision);

    boolean archive(RoomId roomId, Instant archivedAt, Instant deleteAfter, Instant updatedAt);

    boolean updateRole(RoomId roomId, UserId userId, MembershipRole role, Instant updatedAt);

    void addMembership(RoomMembership membership);

    void addMembership(RoomMembership membership, String joinedVia);

    void incrementMemberCount(RoomId roomId, Instant updatedAt);

    void transferOwnership(RoomId roomId, UserId oldOwner, UserId newOwner, Instant updatedAt);

    record AuthorizedRoom(Room room, MembershipRole role) {
    }
}
