-- ============================================================
-- WALLET TABLE
-- Sharded on wallet_id (Snowflake ID)
-- version column enables optimistic locking fallback
-- ============================================================
CREATE TABLE IF NOT EXISTS wallet (
                                      wallet_id           BIGINT          PRIMARY KEY,
                                      user_id             BIGINT          NOT NULL,
                                      upi_id              VARCHAR(50)     NOT NULL UNIQUE,
    balance             DECIMAL(19,4)   NOT NULL DEFAULT 0.0000,
    version             BIGINT          NOT NULL DEFAULT 0,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
    );

-- ============================================================
-- USER TABLE
-- Stores login credentials + links to wallet
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
                                     user_id             BIGINT          PRIMARY KEY,
                                     username            VARCHAR(50)     NOT NULL UNIQUE,
    email               VARCHAR(100)    NOT NULL UNIQUE,
    password_hash       VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW()
    );

-- ============================================================
-- SAGA STATE TABLE
-- Pinned to shard0 only (no sharding)
-- Tracks every transfer saga end to end
-- ============================================================
CREATE TABLE IF NOT EXISTS saga_state (
                                          saga_id             VARCHAR(36)     PRIMARY KEY,
    idempotency_key     VARCHAR(36)     NOT NULL UNIQUE,
    sender_wallet_id    BIGINT          NOT NULL,
    receiver_wallet_id  BIGINT          NOT NULL,
    amount              DECIMAL(19,4)   NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'INITIATED',
    current_step        VARCHAR(30)     NOT NULL DEFAULT 'DEBIT',
    failure_reason      TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_saga_status CHECK (status IN (
                                      'INITIATED',
                                      'DEBITED',
                                      'CREDITED',
                                      'COMPLETED',
                                      'DEBIT_FAILED',
                                      'CREDIT_FAILED',
                                      'ROLLBACK_INITIATED',
                                      'ROLLBACK_COMPLETE'
                                                ))
    );

-- ============================================================
-- TRANSACTION OUTBOX TABLE
-- Sharded on wallet_id
-- Kafka messages are written here first (same DB transaction)
-- A separate scheduler picks them up and publishes to Kafka
-- Prevents message loss if app crashes mid-saga
-- ============================================================
CREATE TABLE IF NOT EXISTS transaction_outbox (
                                                  outbox_id           BIGSERIAL,
                                                  wallet_id           BIGINT          NOT NULL,
                                                  saga_id             VARCHAR(36)     NOT NULL,
    event_type          VARCHAR(50)     NOT NULL,
    payload             TEXT            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMP,

    PRIMARY KEY (outbox_id, wallet_id),

    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
    );

-- ============================================================
-- TRANSACTION HISTORY TABLE
-- Immutable audit log — one row per completed leg
-- Sharded on wallet_id
-- ============================================================
CREATE TABLE IF NOT EXISTS transaction_history (
                                                   txn_id              BIGINT          PRIMARY KEY,
                                                   wallet_id           BIGINT          NOT NULL,
                                                   saga_id             VARCHAR(36)     NOT NULL,
    txn_type            VARCHAR(10)     NOT NULL,
    amount              DECIMAL(19,4)   NOT NULL,
    balance_before      DECIMAL(19,4)   NOT NULL,
    balance_after       DECIMAL(19,4)   NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_txn_type CHECK (txn_type IN ('DEBIT', 'CREDIT', 'ROLLBACK'))
    );

-- ============================================================
-- INDEXES
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_wallet_user_id     ON wallet(user_id);
CREATE INDEX IF NOT EXISTS idx_wallet_upi_id      ON wallet(upi_id);
CREATE INDEX IF NOT EXISTS idx_saga_sender        ON saga_state(sender_wallet_id);
CREATE INDEX IF NOT EXISTS idx_saga_idempotency   ON saga_state(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_outbox_status      ON transaction_outbox(status, created_at);
CREATE INDEX IF NOT EXISTS idx_txn_history_wallet ON transaction_history(wallet_id, created_at);