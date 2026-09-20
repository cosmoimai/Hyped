CREATE TABLE app.app_user (
    id uuid PRIMARY KEY,
    status varchar(20) NOT NULL,
    locked_until timestamptz,
    failed_auth_count smallint NOT NULL DEFAULT 0,
    pii_key_reference varchar(255),
    deletion_requested_at timestamptz,
    pii_destroyed_at timestamptz,
    deleted_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT app_user_status_check CHECK (
        status IN ('active', 'locked', 'suspended', 'compromised', 'deletion_pending', 'deleted')
    ),
    CONSTRAINT app_user_failed_auth_count_check CHECK (failed_auth_count >= 0),
    CONSTRAINT app_user_deletion_state_check CHECK (
        (
            status = 'deleted'
            AND deleted_at IS NOT NULL
            AND pii_destroyed_at IS NOT NULL
        )
        OR
        (
            status <> 'deleted'
            AND deleted_at IS NULL
            AND pii_destroyed_at IS NULL
        )
    ),
    CONSTRAINT app_user_locked_state_check CHECK (
        (status = 'locked') = (locked_until IS NOT NULL)
    ),
    CONSTRAINT app_user_timestamp_check CHECK (
        updated_at >= created_at
    )
);

CREATE TABLE app.user_identity (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app.app_user (id) ON DELETE CASCADE,
    provider varchar(16) NOT NULL,
    provider_subject_hmac bytea NOT NULL,
    email_ciphertext bytea,
    email_hmac bytea,
    email_verified boolean NOT NULL DEFAULT false,
    linked_at timestamptz NOT NULL,
    last_verified_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT user_identity_provider_check CHECK (provider IN ('google', 'apple')),
    CONSTRAINT user_identity_subject_length_check CHECK (octet_length(provider_subject_hmac) = 32),
    CONSTRAINT user_identity_email_length_check CHECK (octet_length(email_hmac) = 32),
    CONSTRAINT user_identity_provider_subject_unique UNIQUE (provider, provider_subject_hmac),
    CONSTRAINT user_identity_user_provider_unique UNIQUE (user_id, provider),
    CONSTRAINT user_identity_email_pair_check CHECK (
        (email_ciphertext IS NULL) = (email_hmac IS NULL)
    ),
    CONSTRAINT user_identity_verified_email_check CHECK (
        NOT email_verified OR email_hmac IS NOT NULL
    ),
    CONSTRAINT user_identity_verification_time_check CHECK (
        last_verified_at >= linked_at
    )
);

CREATE TABLE app.device_registration (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app.app_user (id) ON DELETE CASCADE,
    platform varchar(16) NOT NULL,
    installation_id uuid NOT NULL,
    device_name varchar(80) NOT NULL,
    fcm_token_ciphertext bytea,
    fcm_token_fingerprint bytea,
    notifications_enabled boolean NOT NULL DEFAULT false,
    last_seen_at timestamptz NOT NULL,
    invalidated_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT device_registration_platform_check CHECK (platform IN ('android', 'ios')),
    CONSTRAINT device_registration_name_length_check CHECK (char_length(device_name) BETWEEN 1 AND 80),
    CONSTRAINT device_registration_fingerprint_length_check CHECK (
        octet_length(fcm_token_fingerprint) = 32
    ),
    CONSTRAINT device_registration_fcm_pair_check CHECK (
        (fcm_token_ciphertext IS NULL) = (fcm_token_fingerprint IS NULL)
    ),
    CONSTRAINT device_registration_notifications_check CHECK (
        NOT notifications_enabled
        OR (fcm_token_ciphertext IS NOT NULL AND fcm_token_fingerprint IS NOT NULL)
    ),
    CONSTRAINT device_registration_user_installation_unique UNIQUE (user_id, installation_id),
    CONSTRAINT device_registration_user_id_unique UNIQUE (user_id, id)
);

CREATE INDEX device_registration_active_user_idx
    ON app.device_registration (user_id) WHERE invalidated_at IS NULL;

CREATE TABLE app.auth_session (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app.app_user (id) ON DELETE CASCADE,
    device_id uuid,
    token_family_id uuid NOT NULL,
    issued_at timestamptz NOT NULL,
    last_used_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    revoke_reason varchar(32),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT auth_session_user_device_fk FOREIGN KEY (user_id, device_id)
        REFERENCES app.device_registration (user_id, id) ON DELETE CASCADE,
    CONSTRAINT auth_session_token_family_unique UNIQUE (token_family_id),
    CONSTRAINT auth_session_expiry_check CHECK (expires_at > issued_at),
    CONSTRAINT auth_session_revocation_check CHECK (
            (revoked_at IS NULL) = (revoke_reason IS NULL)
    ),
    CONSTRAINT auth_session_activity_time_check CHECK (
        last_used_at >= issued_at
        AND last_used_at <= expires_at
    ),
    CONSTRAINT auth_session_revoked_time_check CHECK (
        revoked_at IS NULL OR revoked_at >= issued_at
    ),
    CONSTRAINT auth_session_reason_length_check CHECK (
        revoke_reason IS NULL
        OR char_length(revoke_reason) BETWEEN 1 AND 32
    ),
    CONSTRAINT auth_session_timestamp_check CHECK (
        updated_at >= created_at
    )
);

CREATE INDEX auth_session_user_revoked_idx ON app.auth_session (user_id, revoked_at);
CREATE INDEX auth_session_expires_idx ON app.auth_session (expires_at);
CREATE INDEX auth_session_user_device_idx ON app.auth_session (user_id, device_id);
-- Queries also compare expires_at with the server clock to exclude expired sessions.
CREATE INDEX auth_session_active_user_idx
    ON app.auth_session (user_id, expires_at) WHERE revoked_at IS NULL;

CREATE TABLE app.refresh_token_record (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES app.auth_session (id) ON DELETE CASCADE,
    token_digest bytea NOT NULL,
    state varchar(16) NOT NULL,
    issued_at timestamptz NOT NULL,
    consumed_at timestamptz,
    expires_at timestamptz NOT NULL,
    replaced_by_id uuid,
    created_at timestamptz NOT NULL,
    CONSTRAINT refresh_token_record_digest_unique UNIQUE (token_digest),
    CONSTRAINT refresh_token_record_digest_length_check CHECK (octet_length(token_digest) = 32),
    CONSTRAINT refresh_token_record_state_check CHECK (state IN ('active', 'consumed', 'revoked')),
    CONSTRAINT refresh_token_record_expiry_check CHECK (expires_at > issued_at),
    -- Revocation preserves consumption history for tokens already used in rotation.
    CONSTRAINT refresh_token_record_consumption_check CHECK (
        (
            state = 'active'
            AND consumed_at IS NULL
            AND replaced_by_id IS NULL
        )
        OR
        (
            state = 'consumed'
            AND consumed_at IS NOT NULL
            AND replaced_by_id IS NOT NULL
        )
        OR state = 'revoked'
    ),
    CONSTRAINT refresh_token_record_consumed_time_check CHECK (
        consumed_at IS NULL
        OR (
            consumed_at >= issued_at
            AND consumed_at <= expires_at
        )
    ),
    CONSTRAINT refresh_token_record_id_session_unique
        UNIQUE (id, session_id),

    CONSTRAINT refresh_token_record_replacement_fk
        FOREIGN KEY (replaced_by_id, session_id)
        REFERENCES app.refresh_token_record (id, session_id)
);

CREATE UNIQUE INDEX refresh_token_record_one_active_per_session_idx
    ON app.refresh_token_record (session_id)
    WHERE state = 'active';

CREATE INDEX refresh_token_record_session_state_idx
    ON app.refresh_token_record (session_id, state);

CREATE INDEX refresh_token_record_replacement_idx
    ON app.refresh_token_record (replaced_by_id);
