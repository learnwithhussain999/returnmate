ALTER TABLE purchases
    ADD COLUMN user_id UUID;

ALTER TABLE purchases
    ADD CONSTRAINT fk_purchases_user
        FOREIGN KEY (user_id)
            REFERENCES users(id);

CREATE INDEX idx_purchases_user_id
    ON purchases(user_id);