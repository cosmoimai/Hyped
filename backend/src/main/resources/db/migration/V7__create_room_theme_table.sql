CREATE TABLE app.room_theme (
    room_id UUID PRIMARY KEY,
    kind VARCHAR(16) NOT NULL,
    preset_key VARCHAR(64) NOT NULL,
    overlay_key VARCHAR(64) NOT NULL,
    updated_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT room_theme_room_fk FOREIGN KEY (room_id)
        REFERENCES app.room (id) ON DELETE CASCADE,
    CONSTRAINT room_theme_updated_by_fk FOREIGN KEY (updated_by_user_id)
        REFERENCES app.app_user (id),
    CONSTRAINT room_theme_kind_check CHECK (kind IN ('preset', 'gradient')),
    CONSTRAINT room_theme_preset_key_check CHECK (char_length(btrim(preset_key)) BETWEEN 1 AND 64),
    CONSTRAINT room_theme_overlay_key_check CHECK (char_length(btrim(overlay_key)) BETWEEN 1 AND 64),
    CONSTRAINT room_theme_updated_at_check CHECK (updated_at >= created_at)
);

INSERT INTO app.room_theme
    (room_id, kind, preset_key, overlay_key, updated_by_user_id, created_at, updated_at)
SELECT id, 'preset', 'soft-blue-01', 'dark-soft', owner_user_id, created_at, updated_at
FROM app.room;
