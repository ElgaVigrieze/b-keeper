-- ============================================================
-- B-Keeper Database Schema
-- V1 - Initial schema
-- ============================================================

-- ============================================================
-- COMPANY (one record - the company using the app)
-- ============================================================
CREATE TABLE IF NOT EXISTS company (
    id                  INTEGER PRIMARY KEY CHECK (id = 1), -- enforces single row
    name                TEXT    NOT NULL,
    reg_number          TEXT    NOT NULL,   -- reģistrācijas numurs, e.g. 41203041233
    vat_number          TEXT,               -- PVN reģ. nr., e.g. LV41203041233
    legal_address       TEXT,
    bank_account        TEXT,               -- IBAN for tax payments
    email               TEXT,
    phone               TEXT,
    fiscal_year_start   TEXT    NOT NULL DEFAULT '01-01',  -- MM-DD
    accounting_type     TEXT    NOT NULL DEFAULT 'SIMPLE', -- SIMPLE | DOUBLE
    created_at          TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- EMPLOYEES
-- ============================================================
CREATE TABLE IF NOT EXISTS employee (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    personal_code       TEXT    NOT NULL UNIQUE,  -- personas kods, e.g. 030284-11631
    first_name          TEXT    NOT NULL,
    last_name           TEXT    NOT NULL,
    position            TEXT,
    monthly_gross       REAL    NOT NULL DEFAULT 0.00,  -- monthly salary (EUR)
    employment_start    TEXT    NOT NULL,               -- ISO date YYYY-MM-DD
    employment_end      TEXT,                           -- NULL = currently employed
    tax_book_submitted  INTEGER NOT NULL DEFAULT 0,     -- 1 = algas nodokļu grāmatiņa iesniegta
    non_taxable_min     REAL    NOT NULL DEFAULT 0.00,  -- neapliekamais minimums (EUR/month)
    active              INTEGER NOT NULL DEFAULT 1,
    created_at          TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at          TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- PAYROLL (monthly salary calculations)
-- Maps to: Darba devēja ziņojums + Ziņas par darba ņēmējiem
-- ============================================================
CREATE TABLE IF NOT EXISTS payroll_entry (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_id             INTEGER NOT NULL REFERENCES employee(id),
    period_year             INTEGER NOT NULL,   -- e.g. 2026
    period_month            INTEGER NOT NULL,   -- 1-12
    gross_salary            REAL    NOT NULL,   -- darba ienākumi (col 3)

    -- Employer-side calculations
    vsaoi_employer          REAL    NOT NULL,   -- 23.59% of gross (col 4)
    risk_duty               REAL    NOT NULL,   -- 0.46% of gross (col 8) uzņēmēj. riska nodeva

    -- Employee-side deductions
    vsaoi_employee          REAL    NOT NULL,   -- 10.50% of gross → pension (col 11)
    solidarity_tax          REAL    NOT NULL DEFAULT 0.00,  -- solidaritātes nodoklis (col 12)
    iin                     REAL    NOT NULL,   -- iedzīvotāju ienākuma nodoklis (col 7)
    net_salary              REAL    NOT NULL,   -- izmaksājamā summa

    -- Corrections (for amended submissions)
    salary_correction       REAL    NOT NULL DEFAULT 0.00,  -- (col 5)
    vsaoi_correction        REAL    NOT NULL DEFAULT 0.00,  -- (col 6)
    pension_correction      REAL    NOT NULL DEFAULT 0.00,  -- (col 13)
    solidarity_correction   REAL    NOT NULL DEFAULT 0.00,  -- (col 14)

    hours_worked            INTEGER NOT NULL DEFAULT 0,     -- nostrādātās stundas (col 10)
    payment_date            TEXT,                           -- obligāto iemaksu datums (col 9)
    paid_in_cash            INTEGER NOT NULL DEFAULT 0,     -- (col 16)

    eds_submission_id       INTEGER REFERENCES eds_submission(id),
    created_at              TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at              TEXT    NOT NULL DEFAULT (datetime('now')),

    UNIQUE(employee_id, period_year, period_month)
);

-- ============================================================
-- OUTGOING INVOICES (izejošie rēķini)
-- Maps to: PVN 1-III annex, income/expense journal
-- ============================================================
CREATE TABLE IF NOT EXISTS invoice_out (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_number      TEXT    NOT NULL,           -- e.g. VS26/36
    issue_date          TEXT    NOT NULL,           -- YYYY-MM-DD
    due_date            TEXT,
    paid_date           TEXT,                       -- NULL = unpaid

    -- Client info
    client_name         TEXT    NOT NULL,
    client_reg_number   TEXT,
    client_vat_number   TEXT,                       -- PVN reģ. nr. for PVN annex

    -- Amounts
    amount_excl_vat     REAL    NOT NULL,           -- summa bez PVN
    vat_rate            REAL    NOT NULL DEFAULT 21.0,  -- VAT %
    vat_amount          REAL    NOT NULL,           -- PVN summa
    amount_incl_vat     REAL    NOT NULL,           -- kopā

    -- PVN form classification
    pvn_line            INTEGER NOT NULL DEFAULT 41,
    -- 41 = standard domestic, 43 = zero rate, 45 = EU supply,
    -- 48 = services abroad, 49 = exempt

    document_type_code  TEXT    NOT NULL DEFAULT '1',
    -- 1 = rēķins, X = kase, T = telecom, A = purchase invoice

    description         TEXT,
    period_year         INTEGER NOT NULL,           -- taksācijas gads
    period_month        INTEGER NOT NULL,           -- taksācijas mēnesis

    -- Status
    status              TEXT    NOT NULL DEFAULT 'DRAFT',
    -- DRAFT | ISSUED | PAID | OVERDUE | CANCELLED

    -- Journal linkage (auto-created)
    journal_entry_id    INTEGER REFERENCES journal_entry(id),

    created_at          TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at          TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- INCOMING INVOICES (ienākošie rēķini)
-- Maps to: PVN 1-I (domestic) and PVN 1-II (EU) annexes
-- ============================================================
CREATE TABLE IF NOT EXISTS invoice_in (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_number      TEXT    NOT NULL,           -- supplier's invoice number
    invoice_date        TEXT    NOT NULL,           -- YYYY-MM-DD
    received_date       TEXT,
    paid_date           TEXT,

    -- Supplier info
    supplier_name       TEXT    NOT NULL,
    supplier_vat_number TEXT,                       -- for PVN annex
    supplier_country    TEXT    NOT NULL DEFAULT 'LV',  -- ISO 2-letter

    -- Amounts in EUR
    amount_excl_vat     REAL    NOT NULL,
    vat_rate            REAL    NOT NULL DEFAULT 21.0,
    vat_amount          REAL    NOT NULL,
    amount_incl_vat     REAL    NOT NULL,

    -- For EU purchases: original currency amounts (PVN 1-II col)
    original_currency   TEXT,                       -- e.g. PLN, CZK (NULL = EUR)
    original_amount     REAL,                       -- amount in original currency

    -- PVN form classification
    pvn_line            INTEGER NOT NULL DEFAULT 62,
    -- 62 = domestic input VAT (PVN 1-I)
    -- 55 = EU goods standard rate (PVN 1-II)
    -- 61 = import

    document_type_code  TEXT    NOT NULL DEFAULT 'A',
    -- A = rēķins, G = EU goods, T = telecom, N = pakalpojumi, SP = simplified

    expense_category    TEXT    NOT NULL DEFAULT 'GENERAL',
    -- GENERAL | FUEL | IT | RENT | SALARY | SERVICES | MATERIALS | OTHER

    deductible_vat      INTEGER NOT NULL DEFAULT 1, -- 1 = priekšnodoklis atskaitāms
    pdf_filename        TEXT,                       -- stored PDF filename

    period_year         INTEGER NOT NULL,
    period_month        INTEGER NOT NULL,

    -- Journal linkage (auto-created)
    journal_entry_id    INTEGER REFERENCES journal_entry(id),

    created_at          TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at          TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- INCOME / EXPENSE JOURNAL  (ieņēmumu un izdevumu uzskaites žurnāls)
-- The legal accounting record for simplified bookkeeping.
-- Auto-populated from invoices and payroll — never entered manually.
-- ============================================================
CREATE TABLE IF NOT EXISTS journal_entry (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    entry_date          TEXT    NOT NULL,           -- YYYY-MM-DD
    description         TEXT    NOT NULL,
    reference           TEXT,                       -- invoice number or payroll ref

    entry_type          TEXT    NOT NULL,
    -- INCOME_INVOICE | EXPENSE_INVOICE | PAYROLL | ASSET_DEPRECIATION | CORRECTION

    income_amount       REAL    NOT NULL DEFAULT 0.00,   -- ieņēmumi
    expense_amount      REAL    NOT NULL DEFAULT 0.00,   -- izdevumi

    period_year         INTEGER NOT NULL,
    period_month        INTEGER NOT NULL,

    -- Source references (at most one will be set)
    invoice_out_id      INTEGER REFERENCES invoice_out(id),
    invoice_in_id       INTEGER REFERENCES invoice_in(id),
    payroll_ref         TEXT,   -- "PAYROLL-{year}-{month}"
    asset_id            INTEGER REFERENCES fixed_asset(id),

    created_at          TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- FIXED ASSETS (pamatlīdzekļi)
-- Equipment, computers, furniture — value > €430, life > 1 year
-- ============================================================
CREATE TABLE IF NOT EXISTS fixed_asset (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    name                    TEXT    NOT NULL,           -- e.g. "MacBook Pro 14"
    acquisition_date        TEXT    NOT NULL,           -- YYYY-MM-DD
    acquisition_cost        REAL    NOT NULL,
    useful_life_years       INTEGER NOT NULL DEFAULT 5,
    depreciation_method     TEXT    NOT NULL DEFAULT 'STRAIGHT_LINE',
    annual_depreciation     REAL    NOT NULL,           -- calculated on save
    accumulated_depreciation REAL   NOT NULL DEFAULT 0.00,
    book_value              REAL    NOT NULL,           -- acquisition_cost - accumulated
    disposal_date           TEXT,
    disposal_value          REAL,
    invoice_in_id           INTEGER REFERENCES invoice_in(id),
    active                  INTEGER NOT NULL DEFAULT 1,
    created_at              TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at              TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- EDS SUBMISSIONS
-- Tracks every document submitted to EDS
-- ============================================================
CREATE TABLE IF NOT EXISTS eds_submission (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    document_type       TEXT    NOT NULL,
    -- PVN | DDZ | ZINAS | GADA_PARSKATS | UIN | PAZINOTAJI

    period_year         INTEGER NOT NULL,
    period_month        INTEGER,                    -- NULL for annual docs

    status              TEXT    NOT NULL DEFAULT 'DRAFT',
    -- DRAFT | READY | SUBMITTED | ACCEPTED | REJECTED

    xml_filename        TEXT,                       -- generated XML file path
    eds_doc_number      TEXT,                       -- EDS confirmation number
    submitted_at        TEXT,
    confirmed_at        TEXT,

    -- Calculated totals (snapshot at submission time)
    total_json          TEXT,                       -- JSON blob of key figures

    created_at          TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at          TEXT    NOT NULL DEFAULT (datetime('now')),

    UNIQUE(document_type, period_year, period_month)
);

-- ============================================================
-- AUDIT LOG
-- Every significant action is logged
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_log (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    action              TEXT    NOT NULL,   -- CREATED | UPDATED | DELETED | SUBMITTED
    entity_type         TEXT    NOT NULL,   -- invoice_out | invoice_in | payroll | etc.
    entity_id           INTEGER,
    description         TEXT,
    created_at          TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- INDEXES
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_invoice_out_period   ON invoice_out  (period_year, period_month);
CREATE INDEX IF NOT EXISTS idx_invoice_out_status   ON invoice_out  (status);
CREATE INDEX IF NOT EXISTS idx_invoice_in_period    ON invoice_in   (period_year, period_month);
CREATE INDEX IF NOT EXISTS idx_invoice_in_supplier  ON invoice_in   (supplier_vat_number);
CREATE INDEX IF NOT EXISTS idx_journal_period       ON journal_entry(period_year, period_month);
CREATE INDEX IF NOT EXISTS idx_journal_type         ON journal_entry(entry_type);
CREATE INDEX IF NOT EXISTS idx_payroll_period       ON payroll_entry(period_year, period_month);
CREATE INDEX IF NOT EXISTS idx_eds_type_period      ON eds_submission(document_type, period_year, period_month);

-- ============================================================
-- SEED: Default company row (edit via Settings screen)
-- ============================================================
INSERT OR IGNORE INTO company (id, name, reg_number, vat_number, legal_address, fiscal_year_start, accounting_type)
VALUES (1, 'My Company SIA', '00000000000', 'LV00000000000', '', '01-01', 'SIMPLE');
