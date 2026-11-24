-- V7: store return policies, and the purchase fields needed to use them.
--
-- Design doc rule: deadline = (delivery date, or purchase date if none) + the store's
-- return days for that kind of item; if a holiday extension applies, use the later date.
--
-- These rows are REFERENCE DATA: the same for every user, changed only by a new
-- migration (e.g. V8 for next year's holiday dates). The app loads them once at startup.
--
-- Where a policy is unclear we choose the SHORTER window. An early reminder costs
-- nothing; a missed return costs the user money.

-- Item categories every table below may use. Kept in sync with the Java ItemCategory enum.
-- GENERAL = "anything without a special rule", so it never appears in the rule tables.

-- ------------------------------------------------------------------ store_policies
CREATE TABLE store_policies (
    -- Natural key: 'amazon', 'bestbuy'. Stable, readable, and a purchase can store it
    -- directly (purchases.store_code) without a join.
    code                VARCHAR(40)  PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    -- NULL = no fixed limit (Costco, for most items).
    default_return_days INTEGER      CHECK (default_return_days > 0),
    policy_url          VARCHAR(500) NOT NULL,
    notes               TEXT,
    -- When someone last checked this against the store's website.
    verified_on         DATE         NOT NULL
);

-- Names a store appears under on receipts and emails, normalised: lowercase letters and
-- digits only ("Macy's" -> "macys", "Amazon.com" -> "amazoncom"). Exact match only, never
-- a fuzzy guess.
CREATE TABLE store_policy_aliases (
    alias      VARCHAR(100) PRIMARY KEY CHECK (alias ~ '^[a-z0-9]+$'),
    store_code VARCHAR(40)  NOT NULL REFERENCES store_policies (code) ON DELETE CASCADE
);

-- Category exceptions: "Amazon, Apple products: 15 days".
CREATE TABLE store_category_rules (
    store_code  VARCHAR(40) NOT NULL REFERENCES store_policies (code) ON DELETE CASCADE,
    category    VARCHAR(30) NOT NULL CHECK (category IN (
                    'ELECTRONICS', 'PHONE', 'APPLE', 'MAJOR_APPLIANCE', 'AIR_CONDITIONER',
                    'FURNITURE', 'MATTRESS', 'JEWELRY_WATCHES', 'BEAUTY', 'PLANTS')),
    return_days INTEGER     NOT NULL CHECK (return_days > 0),
    PRIMARY KEY (store_code, category)
);

-- Holiday extensions: "bought Nov 1 - Dec 31, 2026 -> return by Jan 31, 2027".
CREATE TABLE store_holiday_extensions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_code   VARCHAR(40) NOT NULL REFERENCES store_policies (code) ON DELETE CASCADE,
    -- NULL = every item that has no row of its own.
    category     VARCHAR(30) CHECK (category IN (
                     'ELECTRONICS', 'PHONE', 'APPLE', 'MAJOR_APPLIANCE', 'AIR_CONDITIONER',
                     'FURNITURE', 'MATTRESS', 'JEWELRY_WATCHES', 'BEAUTY', 'PLANTS')),
    -- Which date must fall in the window: when it was bought, or when it arrived (Apple).
    window_basis VARCHAR(10) NOT NULL CHECK (window_basis IN ('PURCHASE', 'DELIVERY')),
    window_start DATE        NOT NULL,
    window_end   DATE        NOT NULL,
    -- NULL = this category is EXCLUDED from the extension (e.g. Best Buy phones keep 14 days).
    return_by    DATE,
    CHECK (window_end >= window_start),
    CHECK (return_by IS NULL OR return_by >= window_end)
);

-- One rule per store, category and window. COALESCE because NULL never equals NULL.
CREATE UNIQUE INDEX uk_store_holiday_extensions
    ON store_holiday_extensions (store_code, COALESCE(category, '*'), window_start);

-- ------------------------------------------------------------------ seed: 10 stores
-- Source: each store's official return policy, checked 2026-11-24. Membership and
-- store-card benefits (Best Buy Plus, Target Circle, Kohl's Card...) and marketplace
-- sellers are NOT modelled yet: everyone gets the standard window.
INSERT INTO store_policies (code, name, default_return_days, policy_url, notes, verified_on) VALUES
('amazon',    'Amazon',     30,   'https://www.amazon.com/returns',
    'Counted from delivery. Third-party sellers may differ; some items (perishables, customised) are non-returnable.', DATE '2026-11-24'),
('walmart',   'Walmart',    90,   'https://www.walmart.com/help/article/walmart-standard-return-policy/adc0dfb692954e67a4de206fb8d9e03a',
    'Marketplace sellers generally 30 days. Opened phones may be exchange-only.', DATE '2026-11-24'),
('costco',    'Costco',     NULL, 'https://customerservice.costco.com/app/answers/detail/a_id/1191',
    'No fixed limit for most items. Diamonds, custom-installed items and precious metals have extra rules.', DATE '2026-11-24'),
('bestbuy',   'Best Buy',   15,   'https://www.bestbuy.com/site/help-topics/return-exchange-policy/pcmcat260800050014.c',
    'My Best Buy Plus/Total members get 60 days (not modelled). Some products have restocking fees.', DATE '2026-11-24'),
('target',    'Target',     90,   'https://www.target.com/help/articles/returns-exchanges/return-policy',
    'Target-owned brands: 1 year with receipt; Circle Card/360 members +30 days (not modelled).', DATE '2026-11-24'),
('homedepot', 'Home Depot', 90,   'https://www.homedepot.com/c/Return_Policy',
    'TVs/computers must be unused and sealed. Special orders may have a 15% restocking fee. Store card: 365 days (not modelled).', DATE '2026-11-24'),
('lowes',     'Lowe''s',    90,   'https://www.lowes.com/l/about/returns',
    'Generators, chainsaws and pressure washers have special restrictions. Store card: 365 days (not modelled).', DATE '2026-11-24'),
('apple',     'Apple',      14,   'https://www.apple.com/shop/help/returns_refund',
    'Only for purchases made directly from Apple. Must include all accessories and packaging.', DATE '2026-11-24'),
('macys',     'Macy''s',    30,   'https://www.customerservice-macys.com/articles/what-is-macys-return-policy',
    'Mail returns cost $9.99 for non-members. Last Act items are final sale.', DATE '2026-11-24'),
('kohls',     'Kohl''s',    90,   'https://cs.kohls.com/app/answers/detail/a_id/218',
    'Kohl''s Card purchases: 120 days (not modelled). Baby registry: up to 365 days.', DATE '2026-11-24');

INSERT INTO store_policy_aliases (alias, store_code) VALUES
('amazon', 'amazon'), ('amazoncom', 'amazon'), ('amzn', 'amazon'),
('walmart', 'walmart'), ('walmartcom', 'walmart'), ('walmartsupercenter', 'walmart'),
('costco', 'costco'), ('costcowholesale', 'costco'), ('costcocom', 'costco'),
('bestbuy', 'bestbuy'), ('bestbuycom', 'bestbuy'),
('target', 'target'), ('targetcom', 'target'),
('homedepot', 'homedepot'), ('thehomedepot', 'homedepot'), ('homedepotcom', 'homedepot'),
('lowes', 'lowes'), ('lowescom', 'lowes'), ('loweshomeimprovement', 'lowes'),
('apple', 'apple'), ('applestore', 'apple'), ('applecom', 'apple'),
('macys', 'macys'), ('macyscom', 'macys'),
('kohls', 'kohls'), ('kohlscom', 'kohls');

-- "48 hours" is stored as 2 days. Categories without a row fall back to a broader one
-- (PHONE -> ELECTRONICS -> the store default), see ItemCategory.java.
INSERT INTO store_category_rules (store_code, category, return_days) VALUES
('amazon',    'APPLE',            15),
('walmart',   'ELECTRONICS',      30),
('walmart',   'PHONE',            14),
('walmart',   'MAJOR_APPLIANCE',   2),
('costco',    'ELECTRONICS',      90),   -- computers, TVs, cameras, tablets, phones
('costco',    'MAJOR_APPLIANCE',  90),
('bestbuy',   'PHONE',            14),   -- activatable devices
('target',    'ELECTRONICS',      30),
('target',    'APPLE',            14),
('target',    'PHONE',            14),
('homedepot', 'ELECTRONICS',      30),   -- TVs and computers
('homedepot', 'FURNITURE',        30),
('homedepot', 'MAJOR_APPLIANCE',   2),
('homedepot', 'AIR_CONDITIONER',   7),
('lowes',     'ELECTRONICS',      30),
('lowes',     'MAJOR_APPLIANCE',   2),
('lowes',     'AIR_CONDITIONER',   2),
('lowes',     'PLANTS',          365),
('macys',     'FURNITURE',         3),   -- scheduled-delivery furniture: contact within 3 days
('macys',     'MATTRESS',        120),
('kohls',     'ELECTRONICS',      30),   -- premium electronics
('kohls',     'JEWELRY_WATCHES',  30),
('kohls',     'BEAUTY',           30),   -- Sephora
('kohls',     'MATTRESS',         30);

-- 2026 holiday season. return_by NULL = category excluded (keeps its normal window).
INSERT INTO store_holiday_extensions (store_code, category, window_basis, window_start, window_end, return_by) VALUES
('amazon',  NULL,              'PURCHASE', DATE '2026-11-01', DATE '2026-12-31', DATE '2027-01-31'),
('amazon',  'APPLE',           'PURCHASE', DATE '2026-11-01', DATE '2026-12-31', DATE '2027-01-15'),
-- Walmart says "subject to exceptions": phones and appliances keep their short windows.
('walmart', NULL,              'PURCHASE', DATE '2026-10-01', DATE '2026-12-31', DATE '2027-01-31'),
('walmart', 'PHONE',           'PURCHASE', DATE '2026-10-01', DATE '2026-12-31', NULL),
('walmart', 'MAJOR_APPLIANCE', 'PURCHASE', DATE '2026-10-01', DATE '2026-12-31', NULL),
-- Window start not published for non-members; Oct 30 taken from the member window. Re-verify.
('bestbuy', NULL,              'PURCHASE', DATE '2026-10-30', DATE '2026-12-31', DATE '2027-01-15'),
('bestbuy', 'PHONE',           'PURCHASE', DATE '2026-10-30', DATE '2026-12-31', NULL),
-- Target extends only electronics and Apple/phones; everything else already has 90 days.
('target',  'ELECTRONICS',     'PURCHASE', DATE '2026-11-01', DATE '2026-12-24', DATE '2027-01-24'),
('target',  'APPLE',           'PURCHASE', DATE '2026-11-01', DATE '2026-12-24', DATE '2027-01-08'),
('target',  'PHONE',           'PURCHASE', DATE '2026-11-01', DATE '2026-12-24', DATE '2027-01-08'),
-- Apple counts from when the order was RECEIVED.
('apple',   NULL,              'DELIVERY', DATE '2026-11-06', DATE '2026-12-25', DATE '2027-01-08'),
('macys',   NULL,              'PURCHASE', DATE '2026-10-05', DATE '2026-12-31', DATE '2027-01-31'),
('macys',   'FURNITURE',       'PURCHASE', DATE '2026-10-05', DATE '2026-12-31', NULL),
-- Sephora at Kohl's has its own extension rules.
('kohls',   NULL,              'PURCHASE', DATE '2026-10-04', DATE '2026-12-30', DATE '2027-01-31'),
('kohls',   'BEAUTY',          'PURCHASE', DATE '2026-10-04', DATE '2026-12-30', NULL);

-- ------------------------------------------------------------------ purchases
ALTER TABLE purchases
    ADD COLUMN delivery_date DATE,
    -- NULL = a store we don't have a policy for.
    ADD COLUMN store_code    VARCHAR(40) REFERENCES store_policies (code) ON DELETE SET NULL,
    ADD COLUMN item_category VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    -- How it was added. Set by the server, never by the client.
    ADD COLUMN source        VARCHAR(10) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE purchases
    ADD CONSTRAINT chk_purchases_delivery_after_purchase
        CHECK (delivery_date IS NULL OR delivery_date >= purchase_date),
    ADD CONSTRAINT chk_purchases_item_category
        CHECK (item_category IN ('GENERAL', 'ELECTRONICS', 'PHONE', 'APPLE', 'MAJOR_APPLIANCE',
                                 'AIR_CONDITIONER', 'FURNITURE', 'MATTRESS', 'JEWELRY_WATCHES',
                                 'BEAUTY', 'PLANTS')),
    ADD CONSTRAINT chk_purchases_source
        CHECK (source IN ('MANUAL', 'PHOTO', 'EMAIL'));

-- New deadline source: the store's holiday extension gave a later date.
ALTER TABLE purchases
    DROP CONSTRAINT chk_purchases_deadline_source,
    ADD CONSTRAINT chk_purchases_deadline_source
        CHECK (deadline_source IN ('RECEIPT', 'MERCHANT_POLICY', 'HOLIDAY_EXTENSION',
                                   'DEFAULT_WINDOW', 'USER_ENTERED'));
