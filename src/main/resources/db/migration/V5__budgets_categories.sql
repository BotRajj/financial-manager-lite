CREATE TABLE budgets (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category     VARCHAR(50)   NOT NULL,
    period       VARCHAR(7)    NOT NULL,
    limit_amount NUMERIC(15,2) NOT NULL,
    spent_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, category, period)
);

CREATE TABLE user_categories (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, name)
);

CREATE INDEX idx_budgets_user_period  ON budgets(user_id, period);
CREATE INDEX idx_user_categories_user ON user_categories(user_id);
