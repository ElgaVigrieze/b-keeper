-- V4: Customer (klients) database for outgoing invoices
CREATE TABLE IF NOT EXISTS customer (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    name             TEXT    NOT NULL,
    reg_number       TEXT,
    vat_number       TEXT,
    country          TEXT    DEFAULT 'LV',
    address          TEXT,
    email            TEXT,
    phone            TEXT,
    -- Defaults applied when creating outgoing invoices
    default_pvn_line INTEGER DEFAULT 41,   -- 41 = domestic 21%, 45 = EU
    default_doc_type TEXT    DEFAULT '1',  -- 1 = rēķins
    default_vat_rate REAL    DEFAULT 21.0,
    notes            TEXT,
    active           INTEGER DEFAULT 1,
    created_at       TEXT,
    updated_at       TEXT
);

CREATE INDEX IF NOT EXISTS idx_customer_vat    ON customer(vat_number);
CREATE INDEX IF NOT EXISTS idx_customer_reg    ON customer(reg_number);
CREATE INDEX IF NOT EXISTS idx_customer_active ON customer(active);
