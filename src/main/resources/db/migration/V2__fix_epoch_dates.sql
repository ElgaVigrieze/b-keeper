-- V2: Fix dates stored as epoch milliseconds by SQLite JDBC driver.
-- The driver sometimes stores LocalDate as epoch millis instead of ISO string.
-- This migration converts them back to YYYY-MM-DD format.

-- invoice_in: invoice_date, received_date, paid_date
UPDATE invoice_in
SET invoice_date = date(CAST(invoice_date AS INTEGER) / 1000, 'unixepoch')
WHERE invoice_date GLOB '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]*';

UPDATE invoice_in
SET received_date = date(CAST(received_date AS INTEGER) / 1000, 'unixepoch')
WHERE received_date IS NOT NULL
  AND received_date GLOB '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]*';

UPDATE invoice_in
SET paid_date = date(CAST(paid_date AS INTEGER) / 1000, 'unixepoch')
WHERE paid_date IS NOT NULL
  AND paid_date GLOB '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]*';

-- invoice_out: issue_date, due_date, paid_date
UPDATE invoice_out
SET issue_date = date(CAST(issue_date AS INTEGER) / 1000, 'unixepoch')
WHERE issue_date GLOB '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]*';

UPDATE invoice_out
SET due_date = date(CAST(due_date AS INTEGER) / 1000, 'unixepoch')
WHERE due_date IS NOT NULL
  AND due_date GLOB '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]*';

UPDATE invoice_out
SET paid_date = date(CAST(paid_date AS INTEGER) / 1000, 'unixepoch')
WHERE paid_date IS NOT NULL
  AND paid_date GLOB '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]*';

-- employee: employment_start, employment_end
UPDATE employee
SET employment_start = date(CAST(employment_start AS INTEGER) / 1000, 'unixepoch')
WHERE employment_start GLOB '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]*';

UPDATE employee
SET employment_end = date(CAST(employment_end AS INTEGER) / 1000, 'unixepoch')
WHERE employment_end IS NOT NULL
  AND employment_end GLOB '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]*';

-- payroll_entry: payment_date
UPDATE payroll_entry
SET payment_date = date(CAST(payment_date AS INTEGER) / 1000, 'unixepoch')
WHERE payment_date IS NOT NULL
  AND payment_date GLOB '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]*';

-- fixed_asset: acquisition_date, disposal_date
UPDATE fixed_asset
SET acquisition_date = date(CAST(acquisition_date AS INTEGER) / 1000, 'unixepoch')
WHERE acquisition_date GLOB '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]*';

UPDATE fixed_asset
SET disposal_date = date(CAST(disposal_date AS INTEGER) / 1000, 'unixepoch')
WHERE disposal_date IS NOT NULL
  AND disposal_date GLOB '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]*';
