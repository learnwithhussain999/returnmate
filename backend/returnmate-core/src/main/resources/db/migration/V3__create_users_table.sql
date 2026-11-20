
CREATE TABLE users (
                       id UUID PRIMARY KEY,

                       email VARCHAR(320) NOT NULL,

                       password_hash VARCHAR(255) NOT NULL,

                       created_at TIMESTAMPTZ NOT NULL,

                       updated_at TIMESTAMPTZ NOT NULL,

                       CONSTRAINT uk_users_email UNIQUE (email),

                       CONSTRAINT chk_users_email_lowercase
                           CHECK (email = LOWER(email)),

                       CONSTRAINT chk_users_email_not_empty
                           CHECK (LENGTH(TRIM(email)) > 0)
);
