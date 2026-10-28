CREATE TABLE accounts (
    id VARCHAR(64) PRIMARY KEY,
    balance NUMERIC(19,2) NOT NULL CHECK (balance >= 0)
);

CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    from_account VARCHAR(64) NOT NULL REFERENCES accounts(id),
    to_account VARCHAR(64) NOT NULL REFERENCES accounts(id),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT distinct_transfer_accounts CHECK (from_account <> to_account)
);

CREATE TABLE idempotency_requests (
    request_key VARCHAR(128) PRIMARY KEY,
    from_account VARCHAR(64) NOT NULL,
    to_account VARCHAR(64) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    transfer_id UUID NOT NULL UNIQUE REFERENCES transfers(id)
);
