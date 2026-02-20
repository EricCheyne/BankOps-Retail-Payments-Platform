ALTER TABLE transfer ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE transfer ADD COLUMN decision_reason TEXT;
ALTER TABLE transfer ADD COLUMN ledger_entry_id UUID;
