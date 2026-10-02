CREATE TABLE app.room_invitation (
    room_id UUID PRIMARY KEY REFERENCES app.room (id) ON DELETE CASCADE,
    generation INTEGER NOT NULL CHECK (generation >= 1),
    link_token_hash BYTEA NOT NULL UNIQUE CHECK (octet_length(link_token_hash) = 32),
    room_code_hmac BYTEA NOT NULL UNIQUE CHECK (octet_length(room_code_hmac) = 32),
    link_token_ciphertext BYTEA NOT NULL,
    room_code_ciphertext BYTEA NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES app.app_user (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL CHECK (updated_at >= created_at)
);

-- Mutation replay is scoped to account and operation, retained for 24 hours.
-- Responses are encrypted because rotation results contain redisplayable credentials.
CREATE TABLE ops.idempotency_record (
    user_id UUID NOT NULL REFERENCES app.app_user (id) ON DELETE CASCADE,
    idempotency_key VARCHAR(128) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    request_hash BYTEA NOT NULL CHECK (octet_length(request_hash) = 32),
    resource_id UUID,
    response_status SMALLINT,
    response_body JSONB,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, idempotency_key, operation)
);
CREATE INDEX idempotency_record_expiry_idx ON ops.idempotency_record (expires_at);
