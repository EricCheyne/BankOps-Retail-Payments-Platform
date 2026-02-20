CREATE TABLE transfer_risk_seen (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    transfer_id UUID NOT NULL,
    from_account_id UUID NOT NULL,
    amount_cents BIGINT NOT NULL
);

CREATE INDEX idx_transfer_risk_seen_from_account_created_at ON transfer_risk_seen (from_account_id, created_at);
