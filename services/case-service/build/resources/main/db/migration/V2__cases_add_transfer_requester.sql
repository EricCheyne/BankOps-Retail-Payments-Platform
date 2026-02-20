ALTER TABLE ops_case ADD COLUMN transfer_requested_by TEXT NOT NULL DEFAULT 'unknown';
ALTER TABLE ops_case ADD COLUMN reasons_json JSONB NOT NULL DEFAULT '[]'::jsonb;
