CREATE TABLE app.user_block (
 id UUID PRIMARY KEY,
 blocker_user_id UUID NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
 blocked_user_id UUID NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
 created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(blocker_user_id,blocked_user_id),
 CHECK(blocker_user_id<>blocked_user_id)
);
CREATE INDEX user_block_reverse ON app.user_block(blocked_user_id,blocker_user_id);
CREATE TABLE app.report (
 id UUID PRIMARY KEY,
 room_id UUID REFERENCES app.room(id) ON DELETE SET NULL,
 reporter_user_id UUID REFERENCES app.app_user(id) ON DELETE SET NULL,
 subject_user_id UUID REFERENCES app.app_user(id) ON DELETE SET NULL,
 media_asset_id UUID REFERENCES app.media_asset(id) ON DELETE SET NULL,
 subject_fingerprint BYTEA NOT NULL,
 subject_type VARCHAR(16) NOT NULL CHECK(subject_type IN ('ROOM','THEME','ACCOUNT')),
 reason_code VARCHAR(32) NOT NULL CHECK(reason_code IN
 ('INAPPROPRIATE_CONTENT','HARASSMENT','HATE_OR_VIOLENCE','SPAM','OTHER')),
 details_ciphertext BYTEA,
 status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','ACTIONED','DISMISSED')),
 resolution VARCHAR(24),
 reviewer_digest BYTEA,
 resolved_at TIMESTAMPTZ,
 retain_until TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX report_queue ON app.report(status,created_at,id);
CREATE INDEX report_rate ON app.report(reporter_user_id,created_at);
CREATE INDEX report_expiry ON app.report(retain_until);
CREATE TABLE ops.safety_request (
 user_id UUID NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
 request_key UUID NOT NULL,
 operation VARCHAR(16) NOT NULL CHECK(operation IN ('REPORT','BLOCK')),
 request_digest BYTEA NOT NULL,
 resource_id UUID NOT NULL,
 removed_count INTEGER NOT NULL DEFAULT 0,
 left_count INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(user_id,request_key,operation)
);
CREATE INDEX safety_request_expiry ON ops.safety_request(created_at);
-- No room FK: access-loss work must survive physical room deletion.
CREATE TABLE ops.room_access_invalidation (
 user_id UUID NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
 room_id UUID NOT NULL,
 version UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(user_id,room_id)
);
CREATE TABLE ops.moderation_audit (
 id UUID PRIMARY KEY,
 report_id UUID REFERENCES app.report(id) ON DELETE CASCADE,
 reviewer_digest BYTEA NOT NULL,
 action VARCHAR(24) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE app.room ADD COLUMN moderation_hold BOOLEAN NOT NULL DEFAULT FALSE;

CREATE FUNCTION app.clear_deleted_reporter_details() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 UPDATE app.report SET details_ciphertext=NULL WHERE reporter_user_id=OLD.id;
 RETURN OLD;
END;
$$;
CREATE TRIGGER clear_deleted_reporter_details BEFORE DELETE ON app.app_user
FOR EACH ROW EXECUTE FUNCTION app.clear_deleted_reporter_details();

CREATE TABLE ops.moderation_rate (
 reviewer_digest BYTEA NOT NULL,
 minute BIGINT NOT NULL,
 attempts INTEGER NOT NULL,
 PRIMARY KEY(reviewer_digest,minute)
);
