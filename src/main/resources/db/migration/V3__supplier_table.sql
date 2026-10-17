-- V3: Supplier database
CREATE TABLE IF NOT EXISTS supplier (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    name                TEXT    NOT NULL,
    reg_number          TEXT,
    vat_number          TEXT,                  -- e.g. LV41203041233
    country             TEXT    NOT NULL DEFAULT 'LV',
    default_pvn_line    INTEGER NOT NULL DEFAULT 62,
    default_doc_type    TEXT    NOT NULL DEFAULT 'A',
    default_vat_rate    REAL    NOT NULL DEFAULT 21.0,
    default_category    TEXT    NOT NULL DEFAULT 'GENERAL',
    -- Search aliases: comma-separated name fragments Ollama might return
    -- e.g. "Neste,Neste Latvia,Neste Latvija SIA"
    aliases             TEXT,
    active              INTEGER NOT NULL DEFAULT 1,
    created_at          TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at          TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_supplier_vat    ON supplier(vat_number);
CREATE INDEX IF NOT EXISTS idx_supplier_name   ON supplier(name);
