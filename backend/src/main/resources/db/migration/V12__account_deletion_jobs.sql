CREATE TABLE ops.account_deletion_job (
    user_id UUID PRIMARY KEY REFERENCES app.app_user(id) ON DELETE CASCADE,
    firebase_uid_ciphertext BYTEA NOT NULL,
    firebase_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    available_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX account_deletion_due_idx ON ops.account_deletion_job(available_at);
