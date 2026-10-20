ALTER TABLE app.device_registration ADD COLUMN push_session_id UUID;
ALTER TABLE app.device_registration ADD CONSTRAINT device_push_session_fk
    FOREIGN KEY (push_session_id) REFERENCES app.auth_session (id) ON DELETE SET NULL;
CREATE UNIQUE INDEX device_active_push_fingerprint_idx ON app.device_registration (fcm_token_fingerprint)
    WHERE notifications_enabled AND fcm_token_fingerprint IS NOT NULL;

-- Attach revocation cleanup to the existing lifecycle, including refresh-token reuse and account deletion.
CREATE FUNCTION app.clear_revoked_session_push() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' OR NEW.revoked_at IS NOT NULL THEN
        UPDATE app.device_registration SET fcm_token_ciphertext = NULL, fcm_token_fingerprint = NULL,
            notifications_enabled = FALSE, push_session_id = NULL
        WHERE push_session_id = OLD.id;
    END IF;
    RETURN OLD;
END;
$$;
CREATE TRIGGER clear_revoked_session_push AFTER UPDATE OF revoked_at ON app.auth_session
    FOR EACH ROW EXECUTE FUNCTION app.clear_revoked_session_push();
CREATE TRIGGER clear_deleted_session_push BEFORE DELETE ON app.auth_session
    FOR EACH ROW EXECUTE FUNCTION app.clear_revoked_session_push();

CREATE FUNCTION app.clear_invalid_device_push() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.invalidated_at IS NOT NULL THEN
        NEW.fcm_token_ciphertext = NULL;
        NEW.fcm_token_fingerprint = NULL;
        NEW.notifications_enabled = FALSE;
        NEW.push_session_id = NULL;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER clear_invalid_device_push BEFORE UPDATE ON app.device_registration
    FOR EACH ROW EXECUTE FUNCTION app.clear_invalid_device_push();

CREATE FUNCTION app.clear_inactive_account_push() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status <> 'active' THEN
        UPDATE app.device_registration SET fcm_token_ciphertext = NULL, fcm_token_fingerprint = NULL,
            notifications_enabled = FALSE, push_session_id = NULL WHERE user_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER clear_inactive_account_push AFTER UPDATE OF status ON app.app_user
    FOR EACH ROW EXECUTE FUNCTION app.clear_inactive_account_push();

CREATE TABLE app.reminder_delivery (
    id UUID PRIMARY KEY,
    reminder_id UUID NOT NULL REFERENCES app.room_reminder (id) ON DELETE CASCADE,
    room_id UUID NOT NULL REFERENCES app.room (id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES app.device_registration (id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES app.auth_session (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app.app_user (id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('day_before', 'hour_before', 'event_time')),
    event_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'sending', 'sent', 'canceled', 'failed')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    available_at TIMESTAMPTZ NOT NULL,
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (room_id, kind, event_at, device_id)
);
CREATE INDEX reminder_delivery_due_idx ON app.reminder_delivery (available_at)
    WHERE status IN ('pending', 'sending');
