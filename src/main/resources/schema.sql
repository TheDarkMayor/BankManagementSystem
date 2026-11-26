-- ============================================================
-- BANKING TRANSACTION SYSTEM — DATABASE SCHEMA
-- Target: PostgreSQL 16+
-- Encoding: UTF-8
-- ============================================================

-- ──────────────────────────────────────────────────────────────
-- TABLE: customer
-- ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS customer (
    customer_id     BIGSERIAL       PRIMARY KEY,
    national_id     VARCHAR(20)     NOT NULL UNIQUE,
    full_name       VARCHAR(100)    NOT NULL,
    email           VARCHAR(100),
    phone           VARCHAR(15),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
) ^^^

COMMENT ON TABLE  customer IS 'Banking customer identity records' ^^^
COMMENT ON COLUMN customer.national_id IS 'Unique government-issued identifier (Aadhaar/PAN)' ^^^

-- ──────────────────────────────────────────────────────────────
-- TABLE: account
-- Critical: CHECK constraint on balance prevents overdrafts
-- ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS account (
    account_id      VARCHAR(20)     PRIMARY KEY,
    customer_id     BIGINT          NOT NULL REFERENCES customer(customer_id),
    balance         DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'INR',
    status          VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_account_status       CHECK (status IN ('ACTIVE', 'INACTIVE', 'CLOSED'))
) ^^^

COMMENT ON TABLE  account IS 'Customer financial accounts with balance invariant enforcement' ^^^
COMMENT ON COLUMN account.balance IS 'Current available balance — CHECK constraint prevents negative values' ^^^
COMMENT ON COLUMN account.version IS 'Optimistic locking counter — incremented on every balance mutation' ^^^

-- ──────────────────────────────────────────────────────────────
-- TABLE: bank_transaction
-- Critical: PK on transaction_id enforces idempotency
-- ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS bank_transaction (
    transaction_id          VARCHAR(36)     PRIMARY KEY,
    type                    VARCHAR(12)     NOT NULL,
    source_account_id       VARCHAR(20)     REFERENCES account(account_id),
    destination_account_id  VARCHAR(20)     REFERENCES account(account_id),
    amount                  DECIMAL(15,2)   NOT NULL,
    status                  VARCHAR(10)     NOT NULL,
    channel                 VARCHAR(10)     NOT NULL DEFAULT 'SYSTEM',
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_txn_type    CHECK (type   IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT chk_txn_status  CHECK (status IN ('SUCCESS', 'FAILED', 'REJECTED')),
    CONSTRAINT chk_txn_amount  CHECK (amount > 0),
    CONSTRAINT chk_txn_account_logic CHECK (
        (type = 'DEPOSIT'    AND source_account_id IS NULL
                              AND destination_account_id IS NOT NULL) OR
        (type = 'WITHDRAWAL' AND source_account_id IS NOT NULL
                              AND destination_account_id IS NULL)     OR
        (type = 'TRANSFER'   AND source_account_id IS NOT NULL
                              AND destination_account_id IS NOT NULL
                              AND source_account_id <> destination_account_id)
    )
) ^^^

COMMENT ON TABLE  bank_transaction
    IS 'Immutable financial transaction ledger — append-only, never UPDATE' ^^^
COMMENT ON COLUMN bank_transaction.transaction_id
    IS 'Client-supplied UUID — primary idempotency enforcement mechanism' ^^^

-- ──────────────────────────────────────────────────────────────
-- TABLE: transaction_audit
-- ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS transaction_audit (
    audit_id            BIGSERIAL       PRIMARY KEY,
    transaction_id      VARCHAR(36)     NOT NULL
                        REFERENCES bank_transaction(transaction_id),
    thread_name         VARCHAR(50),
    duration_ms         BIGINT,
    execution_status    VARCHAR(10),
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
) ^^^

COMMENT ON TABLE transaction_audit
    IS 'Performance and execution metadata — which thread, how long, outcome' ^^^

-- ──────────────────────────────────────────────────────────────
-- TABLE: failed_transaction
-- ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS failed_transaction (
    failure_id          BIGSERIAL       PRIMARY KEY,
    transaction_id      VARCHAR(36)     NOT NULL
                        REFERENCES bank_transaction(transaction_id),
    error_category      VARCHAR(30)     NOT NULL,
    failure_reason      TEXT,
    failed_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
) ^^^

COMMENT ON TABLE  failed_transaction
    IS 'Structured failure records for analytics and diagnostics' ^^^
COMMENT ON COLUMN failed_transaction.error_category
    IS 'Enumerated: INSUFFICIENT_FUNDS, ACCOUNT_NOT_FOUND, ACCOUNT_INACTIVE, DUPLICATE_TRANSACTION, INVALID_AMOUNT, SELF_TRANSFER, INTERNAL_ERROR' ^^^

-- ──────────────────────────────────────────────────────────────
-- INDEXES
-- ──────────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_account_customer
    ON account(customer_id) ^^^

CREATE INDEX IF NOT EXISTS idx_txn_source_account
    ON bank_transaction(source_account_id, created_at DESC) ^^^

CREATE INDEX IF NOT EXISTS idx_txn_dest_account
    ON bank_transaction(destination_account_id, created_at DESC) ^^^

CREATE INDEX IF NOT EXISTS idx_txn_success_type
    ON bank_transaction(status, type, created_at DESC)
    WHERE status = 'SUCCESS' ^^^

CREATE INDEX IF NOT EXISTS idx_failed_category
    ON failed_transaction(error_category) ^^^

CREATE INDEX IF NOT EXISTS idx_audit_txn_id
    ON transaction_audit(transaction_id) ^^^

-- ──────────────────────────────────────────────────────────────
-- TRIGGER: Auto-update updated_at on account balance change
-- ──────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_account_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql ^^^

CREATE OR REPLACE TRIGGER trg_account_updated_at
    BEFORE UPDATE ON account
    FOR EACH ROW
    EXECUTE FUNCTION update_account_timestamp() ^^^
