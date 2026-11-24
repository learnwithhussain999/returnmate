-- V6: let users sign in with Google.

-- Google users have no ReturnMate password.
ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;

-- Google's "sub" claim: a permanent id for the Google account. We match on this, never
-- on email, because a Google account's email address can change.
ALTER TABLE users
    ADD COLUMN google_subject VARCHAR(255);

ALTER TABLE users
    ADD CONSTRAINT uk_users_google_subject UNIQUE (google_subject),

    -- Every account needs at least one way to sign in.
    ADD CONSTRAINT chk_users_has_sign_in_method
        CHECK (password_hash IS NOT NULL OR google_subject IS NOT NULL);
