ALTER TABLE app.refresh_token_record
    DROP CONSTRAINT refresh_token_record_replacement_fk;

ALTER TABLE app.refresh_token_record
    ADD CONSTRAINT refresh_token_record_replacement_fk
    FOREIGN KEY (replaced_by_id, session_id)
    REFERENCES app.refresh_token_record (id, session_id)
    DEFERRABLE INITIALLY DEFERRED;
