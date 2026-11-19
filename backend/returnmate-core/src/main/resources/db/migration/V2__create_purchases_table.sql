
CREATE TABLE purchases (
                           id UUID PRIMARY KEY,

                           merchant_name VARCHAR(150) NOT NULL,
                           product_name VARCHAR(255) NOT NULL,

                           purchase_date DATE NOT NULL,

                           purchase_amount NUMERIC(12, 2) NOT NULL
                               CHECK (purchase_amount > 0),

                           currency VARCHAR(3) NOT NULL,

                           created_at TIMESTAMPTZ NOT NULL,
                           updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_purchases_purchase_date
    ON purchases (purchase_date);

CREATE INDEX idx_purchases_merchant_name
    ON purchases (merchant_name);
