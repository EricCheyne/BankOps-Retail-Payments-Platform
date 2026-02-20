CREATE TABLE IF NOT EXISTS journal_entry (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    correlation_id TEXT NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE,
    reason_code TEXT,
    created_by TEXT NOT NULL,
    metadata_json JSONB DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS posting_leg (
    id UUID PRIMARY KEY,
    entry_id UUID NOT NULL REFERENCES journal_entry(id),
    account_id UUID NOT NULL,
    direction TEXT CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_cents BIGINT CHECK (amount_cents > 0),
    currency TEXT NOT NULL,
    balance_type TEXT CHECK (balance_type IN ('LEDGER', 'AVAILABLE'))
);

CREATE INDEX idx_posting_leg_account_id ON posting_leg(account_id);
CREATE INDEX idx_posting_leg_entry_id ON posting_leg(entry_id);
