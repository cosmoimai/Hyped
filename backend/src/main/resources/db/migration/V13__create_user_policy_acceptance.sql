CREATE TABLE app.user_policy_acceptance (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app.app_user(id) ON DELETE CASCADE,
    terms_version VARCHAR(32) NOT NULL,
    privacy_version VARCHAR(32) NOT NULL,
    content_rules_version VARCHAR(32) NOT NULL,
    adult_affirmed BOOLEAN NOT NULL CHECK (adult_affirmed),
    accepted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, terms_version, privacy_version, content_rules_version)
);
CREATE INDEX user_policy_acceptance_history_idx ON app.user_policy_acceptance(user_id, accepted_at DESC);
