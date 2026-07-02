CREATE TABLE credit_cards (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id               BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bank_id               BIGINT        REFERENCES banks(id) ON DELETE SET NULL,
    name                  VARCHAR(100)  NOT NULL,
    credit_limit          NUMERIC(15,2) NOT NULL,
    used_amount           NUMERIC(15,2) NOT NULL DEFAULT 0,
    closing_day           INT           NOT NULL,
    due_day               INT           NOT NULL,
    minimum_payment_rate  DECIMAL(5,4)  NOT NULL DEFAULT 0.1500,
    rotating_credit_rate  DECIMAL(7,4)  NOT NULL DEFAULT 0.1500,
    installment_rate      DECIMAL(7,4)  NOT NULL DEFAULT 0.1590,
    late_payment_fine     DECIMAL(5,4)  NOT NULL DEFAULT 0.0200,
    late_payment_interest DECIMAL(5,4)  NOT NULL DEFAULT 0.0100,
    iof_daily_rate        DECIMAL(8,6)  NOT NULL DEFAULT 0.000082,
    iof_additional_rate   DECIMAL(5,4)  NOT NULL DEFAULT 0.0038,
    active                BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE card_numbers (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credit_card_id   BIGINT       NOT NULL REFERENCES credit_cards(id) ON DELETE CASCADE,
    last_four_digits VARCHAR(4)   NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
