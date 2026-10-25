package com.hyped.app.media.infrastructure;

import com.hyped.app.media.application.port.out.MediaStore;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcMediaStore implements MediaStore {
    private final JdbcTemplate jdbc;

    public JdbcMediaStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean active(UUID owner) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM app.app_user WHERE id=? AND status='active')",
                        Boolean.class,
                        owner));
    }

    @Override
    public boolean inFlight(UUID owner, Instant since) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM app.media_asset WHERE owner_user_id=? AND"
                            + " status='PROCESSING' AND updated_at>?)",
                        Boolean.class,
                        owner,
                        Timestamp.from(since)));
    }

    @Override
    public int recent(UUID owner, Instant since) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM app.media_asset WHERE owner_user_id=? AND created_at>?",
                Integer.class,
                owner,
                Timestamp.from(since));
    }

    @Override
    public void insert(Asset a, Instant now) {
        jdbc.update(
                """
                INSERT INTO app.media_asset(id,owner_user_id,content_type,size_bytes,width_px,height_px,
                 content_sha256,status,upload_expires_at,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,'PENDING',?,?,?)
                """,
                a.id(),
                a.owner(),
                a.type(),
                a.size(),
                a.width(),
                a.height(),
                a.sha(),
                Timestamp.from(a.expires()),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private static Asset map(java.sql.ResultSet r, int row) throws java.sql.SQLException {
        return new Asset(
                r.getObject("id", UUID.class),
                r.getObject("owner_user_id", UUID.class),
                r.getString("content_type"),
                r.getInt("size_bytes"),
                r.getInt("width_px"),
                r.getInt("height_px"),
                r.getString("content_sha256"),
                r.getString("status"),
                r.getObject("claim_token", UUID.class),
                r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("upload_expires_at").toInstant(),
                r.getInt("attempts"));
    }

    @Override
    public Asset owned(UUID id, UUID owner) {
        return jdbc
                .query(
                        "SELECT * FROM app.media_asset WHERE id=? AND owner_user_id=?",
                        JdbcMediaStore::map,
                        id,
                        owner)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public Asset claim(UUID id, UUID owner, UUID claim, Instant now) {
        return jdbc
                .query(
                        """
                        UPDATE app.media_asset SET status='PROCESSING',claim_token=?,attempts=attempts+1,updated_at=?
                        WHERE id=? AND owner_user_id=? AND attempts<3 AND upload_expires_at>?
                        AND (status='PENDING' OR (status='PROCESSING' AND updated_at<?)) RETURNING *
                        """,
                        JdbcMediaStore::map,
                        claim,
                        Timestamp.from(now),
                        id,
                        owner,
                        Timestamp.from(now),
                        Timestamp.from(now.minusSeconds(600)))
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public void finish(UUID id, UUID claim, String state, Instant now) {
        jdbc.update(
                "UPDATE app.media_asset SET status=?,updated_at=? WHERE id=? AND claim_token=? AND"
                    + " status='PROCESSING'",
                state,
                Timestamp.from(now),
                id,
                claim);
    }

    @Override
    public boolean readable(UUID id, UUID actor) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
                        SELECT EXISTS(SELECT 1 FROM app.media_asset a JOIN app.app_user u ON u.id=a.owner_user_id
                         WHERE a.id=? AND a.status='READY' AND u.status='active' AND
                         ((NOT EXISTS(SELECT 1 FROM app.room_theme t WHERE t.media_asset_id=a.id) AND a.owner_user_id=?)
                          OR EXISTS(SELECT 1 FROM app.room_theme t JOIN app.room r ON r.id=t.room_id
                           JOIN app.room_member m ON m.room_id=r.id WHERE t.media_asset_id=a.id
                           AND m.user_id=? AND r.status<>'deleting')))
                        """,
                        Boolean.class,
                        id,
                        actor,
                        actor));
    }

    @Override
    public boolean readyForAttachment(UUID id, UUID owner) {
        return !jdbc.query(
                        "SELECT id FROM app.media_asset WHERE id=? AND owner_user_id=? AND"
                            + " status='READY' AND EXISTS(SELECT 1 FROM app.app_user WHERE id=? AND"
                            + " status='active') FOR SHARE",
                        (rs, n) -> rs.getObject(1),
                        id,
                        owner,
                        owner)
                .isEmpty();
    }

    @Override
    public boolean markUnused(UUID id, UUID owner, Instant now) {
        if (jdbc.query(
                        "SELECT id FROM app.media_asset WHERE id=? AND owner_user_id=? FOR UPDATE",
                        (r, n) -> r.getObject(1),
                        id,
                        owner)
                .isEmpty()) {
            return false;
        }
        return jdbc.update(
                        """
                        UPDATE app.media_asset a SET status='DELETING',updated_at=? WHERE a.id=? AND a.owner_user_id=?
                        AND NOT EXISTS(SELECT 1 FROM app.room_theme t WHERE t.media_asset_id=a.id)
                        """,
                        Timestamp.from(now),
                        id,
                        owner)
                == 1;
    }

    @Override
    public List<UUID> expiredUnused(Instant now) {
        var candidates =
                jdbc.query(
                        """
                        SELECT a.id FROM app.media_asset a WHERE (a.status='DELETING' OR a.created_at<?)
                        AND NOT EXISTS(SELECT 1 FROM app.room_theme t WHERE t.media_asset_id=a.id)
                        ORDER BY a.created_at LIMIT 20 FOR UPDATE OF a SKIP LOCKED
                        """,
                        (r, n) -> r.getObject(1, UUID.class),
                        Timestamp.from(now.minusSeconds(86400)));
        var retired = new java.util.ArrayList<UUID>();
        for (UUID id : candidates) {
            if (jdbc.update(
                            """
                            UPDATE app.media_asset a SET status='DELETING' WHERE a.id=?
                            AND NOT EXISTS(SELECT 1 FROM app.room_theme t WHERE t.media_asset_id=a.id)
                            """,
                            id)
                    == 1) {
                retired.add(id);
            }
        }
        return retired;
    }

    @Override
    public List<UUID> userAssets(UUID owner) {
        return jdbc.query(
                "SELECT id FROM app.media_asset WHERE owner_user_id=? ORDER BY id",
                (r, n) -> r.getObject(1, UUID.class),
                owner);
    }

    @Override
    public void retireUser(UUID owner, Instant now) {
        // Lock rooms before media, as in room edits. Account deletion has already revoked all
        // sessions.
        jdbc.query(
                """
                SELECT r.id FROM app.room r JOIN app.room_theme t ON t.room_id=r.id
                JOIN app.media_asset a ON a.id=t.media_asset_id WHERE a.owner_user_id=? ORDER BY r.id FOR UPDATE OF r
                """,
                (r, n) -> r.getObject(1),
                owner);
        jdbc.update(
                """
                UPDATE app.room SET revision=revision+1,updated_at=? WHERE id IN
                 (SELECT t.room_id FROM app.room_theme t JOIN app.media_asset a ON a.id=t.media_asset_id
                  WHERE a.owner_user_id=?)
                """,
                Timestamp.from(now),
                owner);
        jdbc.update(
                """
                UPDATE app.room_theme SET kind='preset',preset_key='soft-blue-01',
                 media_asset_id=NULL,overlay_key='dark-soft'
                WHERE media_asset_id IN (SELECT id FROM app.media_asset WHERE owner_user_id=?)
                """,
                owner);
        jdbc.update(
                "UPDATE app.media_asset SET status='DELETING',updated_at=? WHERE owner_user_id=?",
                Timestamp.from(now),
                owner);
    }

    @Override
    public void removed(UUID id) {
        jdbc.update(
                "DELETE FROM app.media_asset WHERE id=? AND status='DELETING' AND upload_expires_at"
                    + " < CURRENT_TIMESTAMP - INTERVAL '1 hour'",
                id);
    }
}
