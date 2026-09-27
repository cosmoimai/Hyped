CREATE TABLE app.room (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    title VARCHAR(80) NOT NULL,
    event_at TIMESTAMPTZ NOT NULL,
    event_timezone VARCHAR(64) NOT NULL,
    location VARCHAR(120),
    description VARCHAR(500),
    status VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    member_count SMALLINT NOT NULL DEFAULT 1,
    archived_at TIMESTAMPTZ,
    delete_after TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT room_owner_user_fk FOREIGN KEY (owner_user_id)
        REFERENCES app.app_user (id),
    CONSTRAINT room_id_owner_unique UNIQUE (id, owner_user_id),
    CONSTRAINT room_title_check CHECK (char_length(btrim(title)) BETWEEN 1 AND 80),
    CONSTRAINT room_timezone_check CHECK (char_length(btrim(event_timezone)) BETWEEN 1 AND 64),
    CONSTRAINT room_location_check CHECK (location IS NULL OR char_length(location) BETWEEN 1 AND 120),
    CONSTRAINT room_description_check CHECK (description IS NULL OR char_length(description) BETWEEN 1 AND 500),
    CONSTRAINT room_status_check CHECK (status IN ('active', 'archived', 'deleting')),
    CONSTRAINT room_revision_check CHECK (revision >= 1),
    CONSTRAINT room_member_count_check CHECK (member_count BETWEEN 1 AND 25),
    CONSTRAINT room_archive_fields_check CHECK (
        (status = 'active' AND archived_at IS NULL AND delete_after IS NULL)
        OR (status IN ('archived', 'deleting') AND archived_at IS NOT NULL AND delete_after IS NOT NULL)
    ),
    CONSTRAINT room_delete_after_check CHECK (
        delete_after IS NULL OR delete_after = archived_at + INTERVAL '24 hours'
    ),
    CONSTRAINT room_updated_at_check CHECK (updated_at >= created_at)
);

CREATE TABLE app.room_member (
    room_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    joined_via VARCHAR(16) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (room_id, user_id),
    CONSTRAINT room_member_room_fk FOREIGN KEY (room_id)
        REFERENCES app.room (id) ON DELETE CASCADE,
    CONSTRAINT room_member_user_fk FOREIGN KEY (user_id)
        REFERENCES app.app_user (id),
    CONSTRAINT room_member_role_check CHECK (role IN ('owner', 'co_host', 'member')),
    CONSTRAINT room_member_joined_via_check CHECK (joined_via IN ('created', 'invite_link', 'room_code')),
    CONSTRAINT room_member_updated_at_check CHECK (updated_at >= joined_at)
);

CREATE UNIQUE INDEX room_single_owner_idx
    ON app.room_member (room_id) WHERE role = 'owner';
CREATE INDEX room_owner_status_idx ON app.room (owner_user_id, status);
CREATE INDEX room_active_event_idx ON app.room (event_at, id) WHERE status = 'active';
CREATE INDEX room_archived_delete_idx ON app.room (delete_after, id) WHERE status = 'archived';
CREATE INDEX room_member_user_role_idx ON app.room_member (user_id, role);
CREATE INDEX room_member_room_role_idx ON app.room_member (room_id, role);

ALTER TABLE app.room
    ADD CONSTRAINT room_owner_membership_fk
    FOREIGN KEY (id, owner_user_id)
    REFERENCES app.room_member (room_id, user_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION app.validate_room_membership_consistency()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    checked_room_id UUID;
    expected_owner UUID;
    stored_count INTEGER;
    actual_count INTEGER;
    owner_count INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'room' THEN
        checked_room_id := COALESCE(NEW.id, OLD.id);
    ELSE
        checked_room_id := COALESCE(NEW.room_id, OLD.room_id);
    END IF;
    SELECT owner_user_id, member_count INTO expected_owner, stored_count
    FROM app.room WHERE id = checked_room_id;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;
    SELECT count(*), count(*) FILTER (WHERE role = 'owner')
    INTO actual_count, owner_count
    FROM app.room_member WHERE room_id = checked_room_id;
    IF actual_count <> stored_count OR owner_count <> 1 OR NOT EXISTS (
        SELECT 1 FROM app.room_member
        WHERE room_id = checked_room_id AND user_id = expected_owner
            AND role = 'owner'
    ) THEN
        RAISE EXCEPTION 'room membership consistency violation' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER room_membership_consistency_from_room
AFTER INSERT OR UPDATE ON app.room
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION app.validate_room_membership_consistency();

CREATE CONSTRAINT TRIGGER room_membership_consistency_from_member
AFTER INSERT OR UPDATE OR DELETE ON app.room_member
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION app.validate_room_membership_consistency();
