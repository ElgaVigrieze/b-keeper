-- ============================================================
-- V5: Google Drive import for outgoing invoices
-- ============================================================

-- Link an invoice to the Google Drive file it was imported from
-- (used to mark files as already imported on the Drive import page)
ALTER TABLE invoice_out ADD COLUMN drive_file_id TEXT;

-- Stored copy of the imported document (relative path under documents/invoices-out/)
ALTER TABLE invoice_out ADD COLUMN pdf_filename TEXT;

CREATE INDEX IF NOT EXISTS idx_invoice_out_drive ON invoice_out(drive_file_id);

-- ============================================================
-- APPLICATION SETTINGS (key-value)
-- Used for integration settings, e.g. Google Drive folder id.
-- ============================================================
CREATE TABLE IF NOT EXISTS app_setting (
    setting_key   TEXT PRIMARY KEY,
    setting_value TEXT,
    updated_at    TEXT
);
