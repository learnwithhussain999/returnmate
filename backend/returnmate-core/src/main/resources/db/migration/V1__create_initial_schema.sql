
CREATE TABLE schema_test (
                             id BIGSERIAL PRIMARY KEY,
                             message VARCHAR(255) NOT NULL,
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schema_test (message)
VALUES ('ReturnMate database connected successfully');
