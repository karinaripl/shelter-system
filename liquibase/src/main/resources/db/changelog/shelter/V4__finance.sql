-- Благотворитель (человек или организация)
CREATE TABLE shelter_schema.benefactor (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name  VARCHAR(200) NOT NULL,
    phone      VARCHAR(20)  CHECK (phone ~ '^(\+7|8)[0-9]{10}$'),
    email      VARCHAR(255) CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    notes      TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Пожертвование. Денежное: сумма хранится в finance_transaction (категория DONATION).
-- Натуральное: товары — партии product_batch со ссылкой на пожертвование.
-- benefactor_id = NULL — анонимное пожертвование
CREATE TABLE shelter_schema.donation (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    benefactor_id BIGINT      REFERENCES shelter_schema.benefactor (id) ON DELETE RESTRICT,
    donation_date DATE        NOT NULL DEFAULT CURRENT_DATE,
    donation_type VARCHAR(10) NOT NULL CHECK (donation_type IN ('MONEY', 'GOODS')),
    notes         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_donation_benefactor ON shelter_schema.donation (benefactor_id);
CREATE INDEX idx_donation_date ON shelter_schema.donation (donation_date);

-- Единый журнал движения денег. Баланс = сумма INCOME − сумма EXPENSE
CREATE TABLE shelter_schema.finance_transaction (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_date DATE           NOT NULL DEFAULT CURRENT_DATE,
    direction        VARCHAR(10)    NOT NULL CHECK (direction IN ('INCOME', 'EXPENSE')),
    category         VARCHAR(20)    NOT NULL CHECK (category IN ('DONATION', 'SALARY', 'PURCHASE', 'OTHER')),
    amount           NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    description      TEXT,
    donation_id      BIGINT UNIQUE  REFERENCES shelter_schema.donation (id) ON DELETE RESTRICT,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- Ссылка на пожертвование есть ровно у операций категории DONATION
    CONSTRAINT chk_transaction_donation_link CHECK ((category = 'DONATION') = (donation_id IS NOT NULL)),
    -- Пожертвование — всегда приход; зарплата и закупка — всегда расход
    CONSTRAINT chk_transaction_direction CHECK (
        category = 'OTHER'
        OR (category = 'DONATION' AND direction = 'INCOME')
        OR (category IN ('SALARY', 'PURCHASE') AND direction = 'EXPENSE')
    )
);

CREATE INDEX idx_transaction_date ON shelter_schema.finance_transaction (transaction_date);
CREATE INDEX idx_transaction_category ON shelter_schema.finance_transaction (category);

-- Выплата зарплаты за месяц. Сумма — в связанной операции (категория SALARY)
CREATE TABLE shelter_schema.salary_payment (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id BIGINT      NOT NULL UNIQUE REFERENCES shelter_schema.finance_transaction (id) ON DELETE CASCADE,
    employee_id    BIGINT      NOT NULL REFERENCES shelter_schema.employee (id) ON DELETE RESTRICT,
    period_month   DATE        NOT NULL CHECK (EXTRACT(DAY FROM period_month) = 1),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Не больше одной выплаты сотруднику за месяц
    CONSTRAINT uq_salary_employee_month UNIQUE (employee_id, period_month)
);

CREATE INDEX idx_salary_period ON shelter_schema.salary_payment (period_month);
