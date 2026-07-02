CREATE TABLE spending_projections (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    mode       VARCHAR(20)  NOT NULL DEFAULT 'INCLUDE',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE spending_projection_items (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    projection_id       BIGINT        NOT NULL REFERENCES spending_projections(id) ON DELETE CASCADE,
    item_type           VARCHAR(20)   NOT NULL,
    entry_id            BIGINT        REFERENCES entries(id) ON DELETE CASCADE,
    card_id             BIGINT        REFERENCES credit_cards(id) ON DELETE CASCADE,
    card_mode           VARCHAR(20),
    card_installments   INT,
    card_entrada        NUMERIC(15,2),
    card_day_of_month   INT,
    custom_description  VARCHAR(200),
    custom_amount       NUMERIC(15,2),
    custom_installments INT           NOT NULL DEFAULT 1,
    custom_day_of_month INT           NOT NULL DEFAULT 1,
    custom_type         VARCHAR(10)   NOT NULL DEFAULT 'EXPENSE',
    excluded            BOOLEAN       NOT NULL DEFAULT FALSE,
    confirmed           BOOLEAN       NOT NULL DEFAULT FALSE,
    confirmed_at        TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
