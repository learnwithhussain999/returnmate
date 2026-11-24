-- V5: return tracking on purchases, and make every purchase belong to a user.

-- 1. Purchases saved before user accounts existed (before V4) have no owner.
--    No API call can reach them (every query filters by user_id), so remove them
--    before making user_id required.
DELETE FROM purchases WHERE user_id IS NULL;

ALTER TABLE purchases
    ALTER COLUMN user_id SET NOT NULL;

-- 2. Return tracking columns.
--    return_deadline / deadline_source are optional: some items can't be returned.
--    "Expired" is NOT stored: it is worked out from return_deadline and today's date,
--    so it can never be out of date.
ALTER TABLE purchases
    ADD COLUMN order_number    VARCHAR(100),
    ADD COLUMN return_deadline DATE,
    ADD COLUMN deadline_source VARCHAR(20),
    ADD COLUMN return_status   VARCHAR(20) NOT NULL DEFAULT 'OPEN';

-- 3. Rules the database enforces even if application code has a bug.
ALTER TABLE purchases
    ADD CONSTRAINT chk_purchases_return_status
        CHECK (return_status IN ('OPEN', 'RETURNED', 'KEPT')),

    ADD CONSTRAINT chk_purchases_deadline_source
        CHECK (deadline_source IN ('RECEIPT', 'MERCHANT_POLICY', 'DEFAULT_WINDOW', 'USER_ENTERED')),

    ADD CONSTRAINT chk_purchases_deadline_after_purchase
        CHECK (return_deadline IS NULL OR return_deadline >= purchase_date),

    -- A deadline always says where it came from, and a source never exists without a deadline.
    ADD CONSTRAINT chk_purchases_deadline_has_source
        CHECK ((return_deadline IS NULL) = (deadline_source IS NULL));

-- 4. The dashboard asks "this user's purchases, ordered by deadline".
CREATE INDEX idx_purchases_user_deadline
    ON purchases (user_id, return_deadline);
