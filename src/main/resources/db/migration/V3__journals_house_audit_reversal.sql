-- Balanced journals, house accounts, reversals, audit stream.

ALTER TABLE accounts
    ADD COLUMN account_kind VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';

ALTER TABLE accounts
    DROP CONSTRAINT IF EXISTS chk_accounts_balance_non_negative;

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_kind CHECK (account_kind IN ('CUSTOMER', 'HOUSE'));

ALTER TABLE accounts
    ADD CONSTRAINT chk_customer_balance_non_negative
        CHECK (account_kind <> 'CUSTOMER' OR balance >= 0);

ALTER TABLE ledger_entries
    ADD COLUMN journal_id UUID;

UPDATE ledger_entries
SET journal_id = COALESCE(transfer_id, id)
WHERE journal_id IS NULL;

ALTER TABLE ledger_entries
    ALTER COLUMN journal_id SET NOT NULL;

CREATE INDEX idx_ledger_entries_journal_id ON ledger_entries (journal_id);

ALTER TABLE ledger_entries
    DROP CONSTRAINT IF EXISTS chk_ledger_posting_kind;

ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_posting_kind
        CHECK (posting_kind IN ('TRANSFER', 'OPENING', 'REVERSAL'));

ALTER TABLE ledger_entries
    DROP CONSTRAINT IF EXISTS chk_ledger_transfer_ref;

ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_transfer_ref CHECK (
        (posting_kind = 'OPENING' AND transfer_id IS NULL)
        OR (posting_kind IN ('TRANSFER', 'REVERSAL') AND transfer_id IS NOT NULL)
    );

ALTER TABLE transfers
    ADD COLUMN transfer_kind VARCHAR(20) NOT NULL DEFAULT 'TRANSFER';

ALTER TABLE transfers
    ADD COLUMN reverses_transfer_id UUID REFERENCES transfers (id);

ALTER TABLE transfers
    ADD CONSTRAINT chk_transfers_kind CHECK (transfer_kind IN ('TRANSFER', 'REVERSAL'));

ALTER TABLE transfers
    ADD CONSTRAINT uq_transfers_reverses UNIQUE (reverses_transfer_id);

CREATE TABLE audit_events (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(64) NOT NULL,
    actor_user_id   UUID REFERENCES app_users (id),
    entity_type     VARCHAR(64) NOT NULL,
    entity_id       VARCHAR(64) NOT NULL,
    request_id      VARCHAR(100),
    detail          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_events_entity ON audit_events (entity_type, entity_id);
CREATE INDEX idx_audit_events_created ON audit_events (created_at);

CREATE OR REPLACE FUNCTION forbid_audit_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_no_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW
    EXECUTE PROCEDURE forbid_audit_mutation();

CREATE TRIGGER trg_audit_events_no_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW
    EXECUTE PROCEDURE forbid_audit_mutation();
