CREATE TABLE app.user_profile (
    user_id UUID PRIMARY KEY,
    display_name_ciphertext BYTEA NOT NULL,
    provider_photo_url_ciphertext BYTEA,
    photo_media_id UUID,
    profile_revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT user_profile_user_fk
        FOREIGN KEY (user_id) REFERENCES app.app_user (id) ON DELETE CASCADE,
    CONSTRAINT user_profile_revision_positive_check
        CHECK (profile_revision > 0),
    CONSTRAINT user_profile_timestamp_order_check
        CHECK (updated_at >= created_at)
);

COMMENT ON COLUMN app.user_profile.photo_media_id IS
    'Nullable media reference; add its foreign key when app.media_asset is created';
