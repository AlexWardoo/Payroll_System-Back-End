BEGIN;

ALTER TABLE batches RENAME TO payroll_months;
ALTER TABLE payroll_months RENAME COLUMN name TO label;

ALTER TABLE merchant_reports RENAME COLUMN batch_id TO month_id;

ALTER INDEX IF EXISTS idx_merchant_reports_batch_id RENAME TO idx_merchant_reports_month_id;

ALTER TABLE merchant_reports
    DROP CONSTRAINT IF EXISTS uq_merchant_reports_merchant_batch;

ALTER TABLE merchant_reports
    ADD CONSTRAINT uq_merchant_reports_merchant_month UNIQUE (merchant_id, month_id);

COMMIT;
