-- Rotation consumes the old token (including its replacement ID) before inserting
-- the replacement. Check the same-session reference at commit, while the unique
-- active-token index continues to enforce one active token per session immediately.
ALTER TABLE app.refresh_token_record
    ALTER CONSTRAINT refresh_token_record_replacement_fk DEFERRABLE INITIALLY DEFERRED;
