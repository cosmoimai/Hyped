-- Bounded-lived digests only. No integrity token, JWT, request body or user/device identifier.
CREATE TABLE ops.integrity_replay (
    token_digest varchar(43) PRIMARY KEY,
    request_digest varchar(43) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL
);
CREATE INDEX integrity_replay_expiry_idx ON ops.integrity_replay(expires_at);
CREATE TABLE ops.integrity_rate (
    session_digest varchar(43) NOT NULL,
    minute bigint NOT NULL,
    attempts integer NOT NULL CHECK (attempts BETWEEN 1 AND 61),
    PRIMARY KEY (session_digest, minute)
);
CREATE INDEX integrity_rate_expiry_idx ON ops.integrity_rate(minute);
