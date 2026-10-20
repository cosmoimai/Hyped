-- Room deletion also removes encrypted join/rotation snapshots outside the room FK tree.
CREATE INDEX idempotency_record_resource_idx ON ops.idempotency_record (resource_id)
    WHERE resource_id IS NOT NULL;
