ALTER TABLE line_items
    ADD COLUMN IF NOT EXISTS batch_id BIGINT REFERENCES batches(id) ON DELETE CASCADE;

UPDATE line_items li
SET batch_id = m.batch_id
FROM merchants m
WHERE li.merchant_id = m.id;

ALTER TABLE line_items
    ALTER COLUMN batch_id SET NOT NULL;

ALTER TABLE line_items
    ALTER COLUMN merchant_id DROP NOT NULL;
