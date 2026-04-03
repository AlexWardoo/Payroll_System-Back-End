BEGIN;

DROP TABLE IF EXISTS assignments CASCADE;
DROP TABLE IF EXISTS merchant_reports CASCADE;
DROP TABLE IF EXISTS merchants CASCADE;
DROP TABLE IF EXISTS batches CASCADE;
DROP TYPE IF EXISTS payout_basis CASCADE;

CREATE TYPE payout_basis AS ENUM (
    'MERCHANT_NET',
    'AGENT_NET',
    'AGENT_NET_OVERRIDE'
);

CREATE TABLE batches (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE merchants (
    merchant_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    processor TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE merchant_reports (
    id BIGSERIAL PRIMARY KEY,
    merchant_id TEXT NOT NULL REFERENCES merchants(merchant_id) ON DELETE CASCADE,
    batch_id BIGINT NOT NULL REFERENCES batches(id) ON DELETE CASCADE,

    merchant_name_snapshot TEXT,
    processor_snapshot TEXT,

    transactions INTEGER NOT NULL DEFAULT 0,
    sales_volume NUMERIC(14,2) NOT NULL DEFAULT 0,
    gross_profit NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_additions NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_deductions NUMERIC(14,2) NOT NULL DEFAULT 0,
    net_profit NUMERIC(14,2) NOT NULL DEFAULT 0,
    agent_net NUMERIC(14,2) NOT NULL DEFAULT 0,

    is_new BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_merchant_reports_merchant_batch UNIQUE (merchant_id, batch_id)
);

CREATE TABLE assignments (
    id BIGSERIAL PRIMARY KEY,
    merchant_id TEXT NOT NULL REFERENCES merchants(merchant_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    percentage NUMERIC(7,4) NOT NULL CHECK (percentage >= 0 AND percentage <= 100),
    basis_type payout_basis NOT NULL,
    source_user_id BIGINT NULL REFERENCES users(id) ON DELETE CASCADE,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_assignments_source_logic CHECK (
        (basis_type IN ('MERCHANT_NET', 'AGENT_NET') AND source_user_id IS NULL)
        OR
        (basis_type = 'AGENT_NET_OVERRIDE' AND source_user_id IS NOT NULL AND source_user_id <> user_id)
    )
);

CREATE UNIQUE INDEX uq_assignments_active_rule
ON assignments (
    merchant_id,
    user_id,
    basis_type,
    COALESCE(source_user_id, -1)
)
WHERE active = TRUE;

CREATE INDEX idx_merchant_reports_batch_id
ON merchant_reports(batch_id);

CREATE INDEX idx_merchant_reports_merchant_id
ON merchant_reports(merchant_id);

CREATE INDEX idx_assignments_merchant_id
ON assignments(merchant_id);

CREATE INDEX idx_assignments_user_id
ON assignments(user_id);

CREATE INDEX idx_assignments_source_user_id
ON assignments(source_user_id);

COMMIT;