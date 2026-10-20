CREATE TABLE app.room_reminder_preference (
    room_id UUID NOT NULL,
    user_id UUID NOT NULL,
    remind_24_hours_before BOOLEAN NOT NULL DEFAULT TRUE,
    remind_1_hour_before BOOLEAN NOT NULL DEFAULT TRUE,
    remind_at_event_time BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (room_id, user_id),
    CONSTRAINT room_reminder_preference_room_fk FOREIGN KEY (room_id)
        REFERENCES app.room (id) ON DELETE CASCADE,
    CONSTRAINT room_reminder_preference_user_fk FOREIGN KEY (user_id)
        REFERENCES app.app_user (id) ON DELETE CASCADE,
    CONSTRAINT room_reminder_preference_updated_at_check CHECK (updated_at >= created_at)
);

CREATE INDEX room_reminder_preference_user_idx
    ON app.room_reminder_preference (user_id, room_id);

INSERT INTO app.room_reminder_preference
    (room_id, user_id, remind_24_hours_before, remind_1_hour_before,
     remind_at_event_time, created_at, updated_at)
SELECT room_id, user_id, TRUE, TRUE, TRUE, joined_at, updated_at
FROM app.room_member;
