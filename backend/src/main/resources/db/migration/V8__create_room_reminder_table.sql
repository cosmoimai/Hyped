CREATE TABLE app.room_reminder (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL,
    event_at TIMESTAMPTZ NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL,
    claim_token UUID,
    claimed_at TIMESTAMPTZ,
    claim_expires_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    canceled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT room_reminder_room_fk FOREIGN KEY (room_id)
        REFERENCES app.room (id) ON DELETE CASCADE,
    CONSTRAINT room_reminder_kind_check CHECK (kind IN ('day_before', 'hour_before', 'event_time')),
    CONSTRAINT room_reminder_status_check CHECK (status IN ('pending', 'claimed', 'completed', 'canceled')),
    CONSTRAINT room_reminder_updated_at_check CHECK (updated_at >= created_at),
    CONSTRAINT room_reminder_claim_state_check CHECK (
        (status = 'pending' AND claim_token IS NULL AND claimed_at IS NULL
            AND claim_expires_at IS NULL AND completed_at IS NULL AND canceled_at IS NULL)
        OR (status = 'claimed' AND claim_token IS NOT NULL AND claimed_at IS NOT NULL
            AND claim_expires_at IS NOT NULL AND completed_at IS NULL AND canceled_at IS NULL)
        OR (status = 'completed' AND claim_token IS NOT NULL AND claimed_at IS NOT NULL
            AND claim_expires_at IS NOT NULL AND completed_at IS NOT NULL AND canceled_at IS NULL)
        OR (status = 'canceled' AND completed_at IS NULL AND canceled_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX room_reminder_open_logical_key_idx
    ON app.room_reminder (room_id, kind, event_at)
    WHERE status IN ('pending', 'claimed');
CREATE INDEX room_reminder_due_idx
    ON app.room_reminder (scheduled_at, id) WHERE status = 'pending';
CREATE INDEX room_reminder_room_status_idx
    ON app.room_reminder (room_id, status);

INSERT INTO app.room_reminder
    (id, room_id, kind, event_at, scheduled_at, status, created_at, updated_at)
SELECT md5(id::text || ':' || kind)::uuid, id, kind, event_at, event_at - offset_value,
    'pending', updated_at, updated_at
FROM app.room
CROSS JOIN (VALUES
    ('day_before', INTERVAL '24 hours'),
    ('hour_before', INTERVAL '1 hour'),
    ('event_time', INTERVAL '0 seconds')
) AS reminder(kind, offset_value)
WHERE status = 'active';
