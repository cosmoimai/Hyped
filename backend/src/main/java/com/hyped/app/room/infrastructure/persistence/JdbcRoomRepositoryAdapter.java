package com.hyped.app.room.infrastructure.persistence;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.room.application.port.out.RoomRepository;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.Room;
import com.hyped.app.room.domain.RoomId;
import com.hyped.app.room.domain.RoomMembership;
import com.hyped.app.room.domain.RoomStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRoomRepositoryAdapter implements RoomRepository {
    private static final String PROJECTION = """
            SELECT r.*, m.role AS caller_role
            FROM app.room r
            JOIN app.room_member m ON m.room_id = r.id
            WHERE m.user_id = :userId
            """;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public JdbcRoomRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public void lockUser(UserId userId) {
        jdbc.queryForObject("SELECT id FROM app.app_user WHERE id = ? FOR UPDATE", (rs, row) -> rs.getObject(1),
                userId.value());
    }

    @Override
    public long countActiveOwned(UserId userId) {
        return jdbc.queryForObject("SELECT count(*) FROM app.room WHERE owner_user_id = ? AND status = 'active'",
                Long.class, userId.value());
    }

    @Override
    public long countActiveJoined(UserId userId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM app.room_member m
                JOIN app.room r ON r.id = m.room_id
                WHERE m.user_id = ? AND m.role <> 'owner' AND r.status = 'active'
                """, Long.class, userId.value());
    }

    @Override
    public void create(Room room, RoomMembership owner) {
        jdbc.update("""
                INSERT INTO app.room
                    (id, owner_user_id, title, event_at, event_timezone, location, description,
                     status, revision, member_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'active', 1, 1, ?, ?)
                """, room.id().value(), room.ownerUserId().value(), room.title(), timestamp(room.eventAt()),
                room.eventTimeZone(), room.location(), room.description(), timestamp(room.createdAt()),
                timestamp(room.updatedAt()));
        jdbc.update("""
                INSERT INTO app.room_member
                    (room_id, user_id, role, joined_via, joined_at, updated_at)
                VALUES (?, ?, 'owner', 'created', ?, ?)
                """, owner.roomId().value(), owner.userId().value(), timestamp(owner.joinedAt()),
                timestamp(owner.updatedAt()));
    }

    @Override
    public List<AuthorizedRoom> findByUser(UserId userId, RoomStatus status, int limit) {
        String statusClause = status == null ? "" : " AND r.status = :status";
        String order = status == RoomStatus.ARCHIVED
                ? " ORDER BY r.archived_at DESC, r.id DESC" : " ORDER BY r.event_at ASC, r.id ASC";
        MapSqlParameterSource parameters = new MapSqlParameterSource("userId", userId.value())
                .addValue("status", status == null ? null : database(status))
                .addValue("limit", limit);
        return named.query(PROJECTION + statusClause + order + " LIMIT :limit", parameters, this::mapAuthorized);
    }

    @Override
    public Optional<AuthorizedRoom> findAuthorized(RoomId roomId, UserId userId) {
        return first(named.query(PROJECTION + " AND r.id = :roomId",
                Map.of("userId", userId.value(), "roomId", roomId.value()), this::mapAuthorized));
    }

    @Override
    public Optional<AuthorizedRoom> findAuthorizedForUpdate(RoomId roomId, UserId userId) {
        return first(named.query(PROJECTION + " AND r.id = :roomId FOR UPDATE OF r, m",
                Map.of("userId", userId.value(), "roomId", roomId.value()), this::mapAuthorized));
    }

    @Override
    public Optional<Room> findForUpdate(RoomId roomId) {
        List<Room> rows = jdbc.query("SELECT * FROM app.room WHERE id = ? FOR UPDATE",
                (rs, row) -> mapRoom(rs), roomId.value());
        return first(rows);
    }

    @Override
    public Optional<RoomMembership> findMembershipForUpdate(RoomId roomId, UserId userId) {
        List<RoomMembership> rows = jdbc.query("""
                SELECT room_id, user_id, role, joined_at, updated_at
                FROM app.room_member WHERE room_id = ? AND user_id = ? FOR UPDATE
                """, (rs, row) -> new RoomMembership(new RoomId(rs.getObject("room_id", java.util.UUID.class)),
                        new UserId(rs.getObject("user_id", java.util.UUID.class)), role(rs.getString("role")),
                        instant(rs, "joined_at"), instant(rs, "updated_at")), roomId.value(), userId.value());
        return first(rows);
    }

    @Override
    public boolean updateVisibleFields(Room room, long expectedRevision) {
        return jdbc.update("""
                UPDATE app.room SET title = ?, event_at = ?, event_timezone = ?, location = ?, description = ?,
                    revision = revision + 1, updated_at = ?
                WHERE id = ? AND revision = ? AND status = 'active'
                """, room.title(), timestamp(room.eventAt()), room.eventTimeZone(), room.location(), room.description(),
                timestamp(room.updatedAt()), room.id().value(), expectedRevision) == 1;
    }

    @Override
    public boolean archive(RoomId roomId, Instant archivedAt, Instant deleteAfter, Instant updatedAt) {
        return jdbc.update("""
                UPDATE app.room SET status = 'archived', archived_at = ?, delete_after = ?, updated_at = ?
                WHERE id = ? AND status = 'active'
                """, timestamp(archivedAt), timestamp(deleteAfter), timestamp(updatedAt), roomId.value()) == 1;
    }

    @Override
    public boolean updateRole(RoomId roomId, UserId userId, MembershipRole role, Instant updatedAt) {
        return jdbc.update("""
                UPDATE app.room_member SET role = ?, updated_at = ?
                WHERE room_id = ? AND user_id = ? AND role <> 'owner'
                """, database(role), timestamp(updatedAt), roomId.value(), userId.value()) == 1;
    }

    @Override
    public void addMembership(RoomMembership membership) {
        jdbc.update("""
                INSERT INTO app.room_member
                    (room_id, user_id, role, joined_via, joined_at, updated_at)
                VALUES (?, ?, ?, 'invite_link', ?, ?)
                """, membership.roomId().value(), membership.userId().value(), database(membership.role()),
                timestamp(membership.joinedAt()), timestamp(membership.updatedAt()));
    }

    @Override
    public void incrementMemberCount(RoomId roomId, Instant updatedAt) {
        jdbc.update("UPDATE app.room SET member_count = member_count + 1, updated_at = ? WHERE id = ?",
                timestamp(updatedAt), roomId.value());
    }

    @Override
    public void transferOwnership(RoomId roomId, UserId oldOwner, UserId newOwner, Instant updatedAt) {
        jdbc.update("UPDATE app.room_member SET role = 'co_host', updated_at = ? WHERE room_id = ? AND user_id = ?",
                timestamp(updatedAt), roomId.value(), oldOwner.value());
        jdbc.update("UPDATE app.room_member SET role = 'owner', updated_at = ? WHERE room_id = ? AND user_id = ?",
                timestamp(updatedAt), roomId.value(), newOwner.value());
        jdbc.update("UPDATE app.room SET owner_user_id = ?, updated_at = ? WHERE id = ?",
                newOwner.value(), timestamp(updatedAt), roomId.value());
    }

    private AuthorizedRoom mapAuthorized(ResultSet rs, int row) throws SQLException {
        Room room = mapRoom(rs);
        return new AuthorizedRoom(room, role(rs.getString("caller_role")));
    }

    private Room mapRoom(ResultSet rs) throws SQLException {
        return new Room(new RoomId(rs.getObject("id", java.util.UUID.class)),
                new UserId(rs.getObject("owner_user_id", java.util.UUID.class)), rs.getString("title"),
                instant(rs, "event_at"), rs.getString("event_timezone"), rs.getString("location"),
                rs.getString("description"), status(rs.getString("status")), rs.getLong("revision"),
                rs.getInt("member_count"), nullableInstant(rs, "archived_at"),
                nullableInstant(rs, "delete_after"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, java.time.OffsetDateTime.class).toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        java.time.OffsetDateTime value = rs.getObject(column, java.time.OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static MembershipRole role(String value) {
        return MembershipRole.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static RoomStatus status(String value) {
        return RoomStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static String database(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }
}
