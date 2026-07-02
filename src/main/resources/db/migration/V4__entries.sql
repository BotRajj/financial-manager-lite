CREATE TABLE entries (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id         BIGINT        REFERENCES accounts(id) ON DELETE SET NULL,
    credit_card_id     BIGINT        REFERENCES credit_cards(id) ON DELETE SET NULL,
    bank_id            BIGINT        REFERENCES banks(id) ON DELETE SET NULL,
    description        VARCHAR(255)  NOT NULL,
    amount             NUMERIC(15,2) NOT NULL,
    type               VARCHAR(20)   NOT NULL,
    category           VARCHAR(50),
    notes              VARCHAR(500),
    mode               VARCHAR(20)   NOT NULL DEFAULT 'SIMPLE',
    entry_date         DATE,
    direction          VARCHAR(20)   NOT NULL DEFAULT 'STANDARD',
    person_name        VARCHAR(150),
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    confirmed          BOOLEAN       NOT NULL DEFAULT FALSE,
    -- Campos RECURRING
    frequency          VARCHAR(20),
    day_of_month       INT,
    month_of_year      INT,
    last_applied_month VARCHAR(7),
    active             BOOLEAN       NOT NULL DEFAULT TRUE,
    auto_apply         BOOLEAN       NOT NULL DEFAULT FALSE,
    -- Campos INSTALLMENT
    total_installments INT,
    paid_installments  INT           NOT NULL DEFAULT 0,
    paid_amount        NUMERIC(15,2) NOT NULL DEFAULT 0,
    installment_amount NUMERIC(15,2),
    invoice_month      VARCHAR(7),
    -- Campos de inadimplência e juros
    interest_rate      DECIMAL(7,4),
    late_fee_rate      DECIMAL(5,4),
    default_since_date DATE,
    contract_rate      DECIMAL(7,4),
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE entry_installments (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entry_id           BIGINT        NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
    installment_number INT           NOT NULL,
    amount             NUMERIC(15,2) NOT NULL,
    due_date           DATE,
    confirmed          BOOLEAN       NOT NULL DEFAULT FALSE,
    confirmed_at       TIMESTAMP,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (entry_id, installment_number)
);

CREATE INDEX idx_entries_user_date   ON entries(user_id, entry_date DESC);
CREATE INDEX idx_entries_user_mode   ON entries(user_id, mode);
CREATE INDEX idx_entries_credit_card ON entries(credit_card_id);
CREATE INDEX idx_entry_installments  ON entry_installments(entry_id);
