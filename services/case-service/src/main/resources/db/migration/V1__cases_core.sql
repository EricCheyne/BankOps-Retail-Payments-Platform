CREATE TABLE ops_case (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    transfer_id UUID NOT NULL,
    status TEXT NOT NULL,
    created_by TEXT NOT NULL,
    assigned_to TEXT
);

CREATE TABLE case_decision (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    case_id UUID NOT NULL REFERENCES ops_case(id),
    decision TEXT NOT NULL CHECK (decision IN ('APPROVE', 'REJECT')),
    decision_by TEXT NOT NULL,
    justification TEXT NOT NULL
);
