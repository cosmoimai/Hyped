package com.hyped.app.room.infrastructure.persistence;

import com.hyped.app.identity.domain.UserId;
import com.hyped.app.room.application.RoomOperationException;
import com.hyped.app.room.application.port.out.RoomRepository;
import com.hyped.app.room.domain.MembershipRole;
import com.hyped.app.room.domain.Room;
import com.hyped.app.room.domain.RoomId;
import com.hyped.app.room.domain.RoomMembership;
import com.hyped.app.room.domain.RoomStatus;
import com.hyped.app.room.domain.RoomTheme;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcRoomRepositoryAdapter implements RoomRepository {
    private static final String ROOM_WITH_THEME =
            """
            SELECT r.*, t.kind AS theme_kind, t.preset_key AS theme_preset_key,
                t.media_asset_id AS theme_media_asset_id,
                t.overlay_key AS theme_overlay_key, t.updated_by_user_id AS theme_updated_by_user_id,
                t.created_at AS theme_created_at, t.updated_at AS theme_updated_at
            FROM app.room r
            JOIN app.room_theme t ON t.room_id = r.id
            """;
    private static final String AUTHORIZED_PROJECTION =
            """
            SELECT r.*, t.kind AS theme_kind, t.preset_key AS theme_preset_key,
                t.media_asset_id AS theme_media_asset_id,
                t.overlay_key AS theme_overlay_key, t.updated_by_user_id AS theme_updated_by_user_id,
                t.created_at AS theme_created_at, t.updated_at AS theme_updated_at,
                m.role AS caller_role
            FROM app.room r
            JOIN app.room_theme t ON t.room_id = r.id
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
        var locked = jdbc.query("SELECT id FROM app.app_user WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getObject(1), userId.value());
        if (locked.isEmpty()) {
            throw new RoomOperationException(
                    RoomOperationException.Kind.NOT_FOUND,
                    "ROOM_UNAVAILABLE",
                    "Room unavailable",
                    "The room is unavailable.");
        }
    }

    @Override
    public long countActiveOwned(UserId userId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM app.room WHERE owner_user_id = ? AND status = 'active'",
                Long.class,
                userId.value());
    }

    @Override
    public long countActiveJoined(UserId userId) {
        return jdbc.queryForObject(
                """
                SELECT count(*) FROM app.room_member m
                JOIN app.room r ON r.id = m.room_id
                WHERE m.user_id = ? AND m.role <> 'owner' AND r.status = 'active'
                """,
                Long.class,
                userId.value());
    }

    @Override
    public void create(Room room, RoomMembership owner) {
        jdbc.update(
                """
                INSERT INTO app.room
                    (id, owner_user_id, title, event_at, event_timezone, location, description,
                     status, revision, member_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'active', 1, 1, ?, ?)
                """,
                room.id().value(),
                room.ownerUserId().value(),
                room.title(),
                timestamp(room.eventAt()),
                room.eventTimeZone(),
                room.location(),
                room.description(),
                timestamp(room.createdAt()),
                timestamp(room.updatedAt()));
        jdbc.update(
                """
                INSERT INTO app.room_theme
                    (room_id, kind, preset_key, media_asset_id, overlay_key, updated_by_user_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                room.id().value(),
                database(room.theme().kind()),
                room.theme().presetKey(),
                room.theme().mediaAssetId(),
                room.theme().overlayKey(),
                room.theme().updatedByUserId().value(),
                timestamp(room.theme().createdAt()),
                timestamp(room.theme().updatedAt()));
        jdbc.update(
                """
                INSERT INTO app.room_member
                    (room_id, user_id, role, joined_via, joined_at, updated_at)
                VALUES (?, ?, 'owner', 'created', ?, ?)
                """,
                owner.roomId().value(),
                owner.userId().value(),
                timestamp(owner.joinedAt()),
                timestamp(owner.updatedAt()));
    }

    @Override
    public List<AuthorizedRoom> findByUser(UserId userId, RoomStatus status, int limit) {
        String statusClause = status == null ? "" : " AND r.status = :status";
        String order =
                status == null
                        ? """
                         ORDER BY CASE WHEN r.status = 'active' THEN 0 ELSE 1 END,
                            CASE WHEN r.status = 'active' THEN r.event_at END ASC, r.archived_at DESC, r.id ASC
                        """
                        : status == RoomStatus.ARCHIVED
                                ? " ORDER BY r.archived_at DESC, r.id DESC"
                                : " ORDER BY r.event_at ASC, r.id ASC";
        MapSqlParameterSource parameters = new MapSqlParameterSource("userId", userId.value())
                .addValue("status", status == null ? null : database(status))
                .addValue("limit", limit);
        return named.query(
                AUTHORIZED_PROJECTION + statusClause + order + " LIMIT :limit",
                parameters,
                this::mapAuthorized);
    }

    @Override
    public Optional<AuthorizedRoom> findAuthorized(RoomId roomId, UserId userId) {
        return first(named.query(AUTHORIZED_PROJECTION + " AND r.id = :roomId",
                Map.of("userId", userId.value(), "roomId", roomId.value()), this::mapAuthorized));
    }

    @Override
    public Optional<AuthorizedRoom> findAuthorizedForUpdate(RoomId roomId, UserId userId) {
        // Lock the room first, then read authorization in a fresh READ COMMITTED statement.
        // A writer waiting behind ownership transfer must observe the new caller role.
        if (findForUpdate(roomId).isEmpty()) {
            return Optional.empty();
        }
        return findAuthorized(roomId, userId);
    }

    @Override
    public Optional<Room> findForUpdate(RoomId roomId) {
        List<Room> rows = jdbc.query(ROOM_WITH_THEME + " WHERE r.id = ? FOR UPDATE OF r, t",
                (rs, row) -> mapRoom(rs), roomId.value());
        return first(rows);
    }

    @Override
    public Optional<RoomMembership> findMembershipForUpdate(RoomId roomId, UserId userId) {
        List<RoomMembership> rows =
                jdbc.query(
                        """
                        SELECT room_id, user_id, role, joined_at, updated_at
                        FROM app.room_member WHERE room_id = ? AND user_id = ? FOR UPDATE
                        """,
                        (rs, row) ->
                                new RoomMembership(
                                        new RoomId(rs.getObject("room_id", java.util.UUID.class)),
                                        new UserId(rs.getObject("user_id", java.util.UUID.class)),
                                        role(rs.getString("role")),
                                        instant(rs, "joined_at"),
                                        instant(rs, "updated_at")),
                        roomId.value(),
                        userId.value());
        return first(rows);
    }

    @Override
    public boolean updateVisibleFields(Room room, long expectedRevision) {
        return jdbc.update(
                                """
                                UPDATE app.room SET title = ?, event_at = ?, event_timezone = ?,
                                    location = ?, description = ?,
                                    revision = revision + 1, updated_at = ?
                                WHERE id = ? AND revision = ? AND status = 'active'
                                """,
                                room.title(),
                                timestamp(room.eventAt()),
                                room.eventTimeZone(),
                                room.location(),
                                room.description(),
                                timestamp(room.updatedAt()),
                                room.id().value(),
                                expectedRevision)
                        == 1
                && jdbc.update(
                                """
                                UPDATE app.room_theme
                                SET kind = ?, preset_key = ?, media_asset_id = ?, overlay_key = ?,
                                    updated_by_user_id = ?, updated_at = ?
                                WHERE room_id = ?
                                """,
                                database(room.theme().kind()),
                                room.theme().presetKey(),
                                room.theme().mediaAssetId(),
                                room.theme().overlayKey(),
                                room.theme().updatedByUserId().value(),
                                timestamp(room.theme().updatedAt()),
                                room.id().value())
                        == 1;
    }

    @Override
    public boolean archive(RoomId roomId, Instant archivedAt, Instant deleteAfter, Instant updatedAt) {
        return jdbc.update(
                        """
                        UPDATE app.room SET status = 'archived', archived_at = ?, delete_after = ?,
                            revision = revision + 1, updated_at = ?
                        WHERE id = ? AND status = 'active'
                        """,
                        timestamp(archivedAt),
                        timestamp(deleteAfter),
                        timestamp(updatedAt),
                        roomId.value())
                == 1;
    }

    @Override
    public void restore(RoomId roomId, Instant now) {
        jdbc.update(
                """
                UPDATE app.room SET status = 'active', archived_at = NULL, delete_after = NULL,
                    revision = revision + 1, updated_at = ? WHERE id = ?
                """,
                timestamp(now),
                roomId.value());
    }

    @Override
    public void cancelDeliveries(RoomId roomId, Instant now) {
        jdbc.update(
                """
                UPDATE app.reminder_delivery SET status = 'canceled', updated_at = ?
                WHERE room_id = ? AND status IN ('pending', 'sending')
                """,
                timestamp(now),
                roomId.value());
    }

    @Override
    public void deletePermanently(RoomId roomId) {
        jdbc.update(
                """
                UPDATE app.media_asset a SET status='DELETING' WHERE a.id IN
                 (SELECT media_asset_id FROM app.room_theme WHERE room_id=?)
                 AND NOT EXISTS(SELECT 1 FROM app.room_theme t WHERE t.media_asset_id=a.id AND t.room_id<>?)
                """,
                roomId.value(),
                roomId.value());
        // Clear encrypted replay snapshots explicitly: resource_id intentionally has no room FK.
        jdbc.update("DELETE FROM ops.idempotency_record WHERE resource_id = ?", roomId.value());
        jdbc.update(
                "UPDATE app.room SET status = 'deleting' WHERE id = ? AND status = 'archived'",
                roomId.value());
        jdbc.update("DELETE FROM app.room WHERE id = ? AND status = 'deleting'", roomId.value());
    }

    @Override
    public List<RoomId> lockDeletionDue(Instant now, int limit) {
        return jdbc.query(
                """
                SELECT id FROM app.room WHERE status = 'archived' AND delete_after <= ?
                ORDER BY delete_after, id LIMIT ? FOR UPDATE SKIP LOCKED
                """,
                (rs, row) -> new RoomId(rs.getObject("id", java.util.UUID.class)),
                timestamp(now),
                limit);
    }

    @Override
    public boolean updateRole(RoomId roomId, UserId userId, MembershipRole role, Instant updatedAt) {
        return jdbc.update(
                        """
                        UPDATE app.room_member SET role = ?, updated_at = ?
                        WHERE room_id = ? AND user_id = ? AND role <> 'owner'
                        """,
                        database(role),
                        timestamp(updatedAt),
                        roomId.value(),
                        userId.value())
                == 1;
    }

    @Override
    public void addMembership(RoomMembership membership) {
        addMembership(membership, "invite_link");
    }

    @Override
    public void addMembership(RoomMembership membership, String joinedVia) {
        jdbc.update(
                """
                INSERT INTO app.room_member
                    (room_id, user_id, role, joined_via, joined_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                membership.roomId().value(),
                membership.userId().value(),
                database(membership.role()),
                joinedVia,
                timestamp(membership.joinedAt()),
                timestamp(membership.updatedAt()));
    }

    @Override
    public void incrementMemberCount(RoomId roomId, Instant updatedAt) {
        jdbc.update(
                "UPDATE app.room SET member_count = member_count + 1, updated_at = ? WHERE id = ?",
                timestamp(updatedAt),
                roomId.value());
    }

    @Override
    public void transferOwnership(RoomId roomId, UserId oldOwner, UserId newOwner, Instant updatedAt) {
        jdbc.update(
                "UPDATE app.room_member SET role = 'co_host', updated_at = ? WHERE room_id = ? AND"
                    + " user_id = ?",
                timestamp(updatedAt),
                roomId.value(),
                oldOwner.value());
        jdbc.update(
                "UPDATE app.room_member SET role = 'owner', updated_at = ? WHERE room_id = ? AND"
                    + " user_id = ?",
                timestamp(updatedAt),
                roomId.value(),
                newOwner.value());
        jdbc.update("UPDATE app.room SET owner_user_id = ?, updated_at = ? WHERE id = ?",
                newOwner.value(), timestamp(updatedAt), roomId.value());
    }

    @Override
    public List<RoomMembership> findMembers(RoomId roomId) {
        return jdbc.query(
                """
                SELECT room_id, user_id, role, joined_at, updated_at FROM app.room_member
                WHERE room_id = ? ORDER BY user_id
                """,
                (rs, row) ->
                        new RoomMembership(
                                roomId,
                                new UserId(rs.getObject("user_id", java.util.UUID.class)),
                                role(rs.getString("role")),
                                instant(rs, "joined_at"),
                                instant(rs, "updated_at")),
                roomId.value());
    }

    @Override
    public void removeMembership(RoomId roomId, UserId userId, Instant now) {
        int removed =
                jdbc.update(
                        "DELETE FROM app.room_member WHERE room_id = ? AND user_id = ? AND role <>"
                            + " 'owner'",
                        roomId.value(),
                        userId.value());
        if (removed == 1) {
            jdbc.update(
                    "DELETE FROM app.room_reminder_preference WHERE room_id = ? AND user_id = ?",
                    roomId.value(),
                    userId.value());
            jdbc.update(
                    """
                    UPDATE app.reminder_delivery SET status = 'canceled', updated_at = ?
                    WHERE room_id = ? AND user_id = ? AND status IN ('pending', 'sending')
                    """,
                    timestamp(now),
                    roomId.value(),
                    userId.value());
            jdbc.update(
                    """
                    UPDATE app.room SET member_count = member_count - 1, revision = revision + 1, updated_at = ?
                    WHERE id = ?
                    """,
                    timestamp(now),
                    roomId.value());
        }
    }

    @Override
    public void incrementRevision(RoomId roomId, Instant now) {
        jdbc.update("UPDATE app.room SET revision = revision + 1, updated_at = ? WHERE id = ?",
                timestamp(now), roomId.value());
    }

    private AuthorizedRoom mapAuthorized(ResultSet rs, int row) throws SQLException {
        Room room = mapRoom(rs);
        return new AuthorizedRoom(room, role(rs.getString("caller_role")),rs.getBoolean("moderation_hold"));
    }

    private Room mapRoom(ResultSet rs) throws SQLException {
        RoomId roomId = new RoomId(rs.getObject("id", java.util.UUID.class));
        RoomTheme theme =
                new RoomTheme(
                        roomId,
                        themeKind(rs.getString("theme_kind")),
                        rs.getString("theme_preset_key"),
                        rs.getObject("theme_media_asset_id", java.util.UUID.class),
                        rs.getString("theme_overlay_key"),
                        new UserId(rs.getObject("theme_updated_by_user_id", java.util.UUID.class)),
                        instant(rs, "theme_created_at"),
                        instant(rs, "theme_updated_at"));
        return new Room(
                roomId,
                new UserId(rs.getObject("owner_user_id", java.util.UUID.class)),
                rs.getString("title"),
                instant(rs, "event_at"),
                rs.getString("event_timezone"),
                rs.getString("location"),
                rs.getString("description"),
                theme,
                status(rs.getString("status")),
                rs.getLong("revision"),
                rs.getInt("member_count"),
                nullableInstant(rs, "archived_at"),
                nullableInstant(rs, "delete_after"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
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

    private static RoomTheme.Kind themeKind(String value) {
        return RoomTheme.Kind.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static String database(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }
}
