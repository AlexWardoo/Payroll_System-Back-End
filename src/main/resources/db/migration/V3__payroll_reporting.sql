ALTER TABLE users ADD COLUMN IF NOT EXISTS display_name VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS payout_basis VARCHAR(20) NOT NULL DEFAULT 'NET';
ALTER TABLE users ADD COLUMN IF NOT EXISTS payout_rate DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS can_view_profit BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users
SET display_name = CASE username
    WHEN 'adam' THEN 'Adam Ward'
    WHEN 'victor' THEN 'Victor'
    ELSE UPPER(SUBSTRING(username, 1, 1)) || LOWER(SUBSTRING(username, 2))
END
WHERE display_name IS NULL;

UPDATE users
SET password_hash = '123'
WHERE username IN ('adam', 'victor');

UPDATE users
SET payout_basis = 'NET',
    payout_rate = 32.5,
    can_view_profit = TRUE
WHERE username = 'victor';

INSERT INTO users (username, password_hash, role, display_name, payout_basis, payout_rate, can_view_profit)
SELECT 'simeon', '123', 'EMPLOYEE', 'Simeon', 'AGENT_NET', 30.0, FALSE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'simeon');

INSERT INTO users (username, password_hash, role, display_name, payout_basis, payout_rate, can_view_profit)
SELECT 'caesar', '123', 'EMPLOYEE', 'Caesar', 'AGENT_NET', 30.0, FALSE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'caesar');

CREATE TABLE IF NOT EXISTS payout_overrides (
    id BIGSERIAL PRIMARY KEY,
    beneficiary_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    percentage DOUBLE PRECISION NOT NULL,
    UNIQUE (beneficiary_user_id, source_user_id)
);

INSERT INTO payout_overrides (beneficiary_user_id, source_user_id, percentage)
SELECT beneficiary.id, source.id, 15.0
FROM users beneficiary
JOIN users source ON source.username = 'caesar'
WHERE beneficiary.username = 'simeon'
  AND NOT EXISTS (
      SELECT 1
      FROM payout_overrides po
      WHERE po.beneficiary_user_id = beneficiary.id
        AND po.source_user_id = source.id
  );

ALTER TABLE line_items ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE line_items ADD COLUMN IF NOT EXISTS subject_name VARCHAR(255);
ALTER TABLE line_items ADD COLUMN IF NOT EXISTS income DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE line_items ADD COLUMN IF NOT EXISTS expenses DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE line_items ADD COLUMN IF NOT EXISTS net DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE line_items ADD COLUMN IF NOT EXISTS agent_net DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE line_items ADD COLUMN IF NOT EXISTS percentage DOUBLE PRECISION;

UPDATE line_items
SET net = amount,
    agent_net = amount,
    subject_name = COALESCE(subject_name, description)
WHERE subject_name IS NULL;
