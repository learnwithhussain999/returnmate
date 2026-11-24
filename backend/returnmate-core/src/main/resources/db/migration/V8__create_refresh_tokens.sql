-- V8: refresh tokens, so users stay signed in without a long-lived access token.
--
-- The token itself is NEVER stored, only its SHA-256 hash. A leaked database therefore
-- can't be used to sign in as anyone. (A fast hash is fine here: the token is 256 random
-- bits, so there's nothing to guess. Passwords need bcrypt because people choose them.)

CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL CHECK (token_hash ~ '^[0-9a-f]{64}$'),  -- hex SHA-256
    -- Every token produced by rotating from one sign-in shares a family. If a retired
    -- token is ever used again, the whole family is revoked (see RefreshTokenService).
    family_id  UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,

    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT chk_refresh_tokens_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
-- For a future clean-up job that deletes long-expired rows.
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);
