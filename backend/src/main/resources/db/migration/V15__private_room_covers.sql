CREATE TABLE app.media_asset (
 id UUID PRIMARY KEY,
 owner_user_id UUID NOT NULL REFERENCES app.app_user(id),
 content_type VARCHAR(32) NOT NULL CHECK(content_type IN ('image/jpeg','image/png','image/webp')),
 size_bytes INTEGER NOT NULL CHECK(size_bytes BETWEEN 1 AND 1572864),
 width_px INTEGER NOT NULL CHECK(width_px BETWEEN 1 AND 2048),
 height_px INTEGER NOT NULL CHECK(height_px BETWEEN 1 AND 2048),
 content_sha256 VARCHAR(44) NOT NULL,
 status VARCHAR(24) NOT NULL CHECK(status IN ('PENDING','PROCESSING','READY','MANUAL_REVIEW','REJECTED','DELETING')),
 claim_token UUID,
 attempts INTEGER NOT NULL DEFAULT 0,
 upload_expires_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX media_cleanup ON app.media_asset(status,created_at);
CREATE INDEX media_owner_created ON app.media_asset(owner_user_id,created_at);
ALTER TABLE app.room_theme ADD COLUMN media_asset_id UUID REFERENCES app.media_asset(id);
ALTER TABLE app.room_theme ALTER COLUMN preset_key DROP NOT NULL;
ALTER TABLE app.room_theme DROP CONSTRAINT room_theme_kind_check;
ALTER TABLE app.room_theme ADD CONSTRAINT room_theme_kind_check CHECK(kind IN ('preset','gradient','uploaded_image'));
ALTER TABLE app.room_theme ADD CONSTRAINT room_theme_source_check CHECK(
 (kind IN ('preset','gradient') AND preset_key IS NOT NULL AND media_asset_id IS NULL)
 OR (kind='uploaded_image' AND preset_key IS NULL AND media_asset_id IS NOT NULL));
