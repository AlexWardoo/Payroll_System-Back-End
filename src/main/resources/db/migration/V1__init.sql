CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL
);

CREATE TABLE batches (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE merchants (
    id BIGSERIAL PRIMARY KEY,
    external_merchant_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL REFERENCES batches(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    transactions INTEGER,
    sales_amount DOUBLE PRECISION,
    income DOUBLE PRECISION,
    expenses DOUBLE PRECISION,
    net DOUBLE PRECISION,
    bps DOUBLE PRECISION,
    percentage DOUBLE PRECISION,
    agent_net DOUBLE PRECISION,
    is_new BOOLEAN NOT NULL DEFAULT FALSE,
    processor VARCHAR(100),
    UNIQUE (external_merchant_id, batch_id)
);

CREATE TABLE assignments (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    percentage DOUBLE PRECISION NOT NULL,
    UNIQUE (merchant_id, user_id)
);

CREATE TABLE line_items (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    description VARCHAR(255),
    amount DOUBLE PRECISION NOT NULL,
    notes TEXT
);