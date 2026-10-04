-- Товар. Товары с историей не удаляются, а выводятся из оборота (is_active = FALSE)
CREATE TABLE shelter_schema.product (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    category_id     BIGINT       NOT NULL REFERENCES shelter_schema.product_category (id) ON DELETE RESTRICT,
    unit_of_measure VARCHAR(20)  NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_category ON shelter_schema.product (category_id);

-- Склад (место хранения)
CREATE TABLE shelter_schema.warehouse (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(100) NOT NULL UNIQUE,
    warehouse_type VARCHAR(50),
    capacity       INTEGER      CHECK (capacity > 0),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Поставщик
CREATE TABLE shelter_schema.supplier (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(200) NOT NULL UNIQUE,
    phone      VARCHAR(20)  CHECK (phone ~ '^(\+7|8)[0-9]{10}$'),
    email      VARCHAR(255) CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    address    TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Поставка (закупка у поставщика). transaction_id — оплата (операция категории PURCHASE)
CREATE TABLE shelter_schema.supply (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_id    BIGINT        NOT NULL REFERENCES shelter_schema.supplier (id) ON DELETE RESTRICT,
    supply_date    DATE          NOT NULL DEFAULT CURRENT_DATE,
    employee_id    BIGINT        REFERENCES shelter_schema.employee (id) ON DELETE RESTRICT,
    transaction_id BIGINT UNIQUE REFERENCES shelter_schema.finance_transaction (id) ON DELETE RESTRICT,
    notes          TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_supply_supplier ON shelter_schema.supply (supplier_id);
CREATE INDEX idx_supply_employee ON shelter_schema.supply (employee_id);
CREATE INDEX idx_supply_date ON shelter_schema.supply (supply_date);

-- Партия товара. Источник — ровно один: поставка или натуральное пожертвование.
-- quantity — сколько поступило; текущие остатки — в batch_stock
CREATE TABLE shelter_schema.product_batch (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id      BIGINT         NOT NULL REFERENCES shelter_schema.product (id) ON DELETE RESTRICT,
    supply_id       BIGINT         REFERENCES shelter_schema.supply (id) ON DELETE RESTRICT,
    donation_id     BIGINT         REFERENCES shelter_schema.donation (id) ON DELETE RESTRICT,
    received_date   DATE           NOT NULL DEFAULT CURRENT_DATE,
    expiration_date DATE,
    quantity        NUMERIC(10, 3) NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(12, 2) CHECK (unit_price >= 0),
    employee_id     BIGINT         REFERENCES shelter_schema.employee (id) ON DELETE RESTRICT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_batch_one_source CHECK (num_nonnulls(supply_id, donation_id) = 1),
    CONSTRAINT chk_batch_expiration CHECK (expiration_date IS NULL OR expiration_date > received_date),
    -- Цена закупки бывает только у партии из поставки
    CONSTRAINT chk_batch_price_only_for_supply CHECK (unit_price IS NULL OR supply_id IS NOT NULL)
);

CREATE INDEX idx_batch_product ON shelter_schema.product_batch (product_id);
CREATE INDEX idx_batch_supply ON shelter_schema.product_batch (supply_id);
CREATE INDEX idx_batch_donation ON shelter_schema.product_batch (donation_id);
CREATE INDEX idx_batch_employee ON shelter_schema.product_batch (employee_id);
-- Частый фильтр: партии с истекающим сроком годности
CREATE INDEX idx_batch_expiration ON shelter_schema.product_batch (expiration_date);

-- Остаток партии на конкретном складе (одна партия может лежать на нескольких складах)
CREATE TABLE shelter_schema.batch_stock (
    batch_id     BIGINT         NOT NULL REFERENCES shelter_schema.product_batch (id) ON DELETE CASCADE,
    warehouse_id BIGINT         NOT NULL REFERENCES shelter_schema.warehouse (id) ON DELETE RESTRICT,
    quantity     NUMERIC(10, 3) NOT NULL CHECK (quantity >= 0),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (batch_id, warehouse_id)
);

CREATE INDEX idx_stock_warehouse ON shelter_schema.batch_stock (warehouse_id);

-- Расход товара (использование). Берётся с конкретного склада — ссылка на строку остатка.
-- animal_id — на какое животное израсходовано (NULL — общий расход, например наполнитель)
CREATE TABLE shelter_schema.product_expense (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id     BIGINT         NOT NULL,
    warehouse_id BIGINT         NOT NULL,
    employee_id  BIGINT         NOT NULL REFERENCES shelter_schema.employee (id) ON DELETE RESTRICT,
    animal_id    BIGINT         REFERENCES shelter_schema.animal (id) ON DELETE RESTRICT,
    quantity     NUMERIC(10, 3) NOT NULL CHECK (quantity > 0),
    expense_date DATE           NOT NULL DEFAULT CURRENT_DATE,
    notes        TEXT,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT fk_expense_stock FOREIGN KEY (batch_id, warehouse_id)
        REFERENCES shelter_schema.batch_stock (batch_id, warehouse_id) ON DELETE RESTRICT
);

CREATE INDEX idx_expense_stock ON shelter_schema.product_expense (batch_id, warehouse_id);
CREATE INDEX idx_expense_warehouse ON shelter_schema.product_expense (warehouse_id);
CREATE INDEX idx_expense_employee ON shelter_schema.product_expense (employee_id);
CREATE INDEX idx_expense_animal ON shelter_schema.product_expense (animal_id);
CREATE INDEX idx_expense_date ON shelter_schema.product_expense (expense_date);

-- Списание товара (просрочка, порча, утеря) — в отличие от прототипа, с записью причины
CREATE TABLE shelter_schema.product_write_off (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id       BIGINT         NOT NULL,
    warehouse_id   BIGINT         NOT NULL,
    employee_id    BIGINT         NOT NULL REFERENCES shelter_schema.employee (id) ON DELETE RESTRICT,
    quantity       NUMERIC(10, 3) NOT NULL CHECK (quantity > 0),
    write_off_date DATE           NOT NULL DEFAULT CURRENT_DATE,
    reason         VARCHAR(20)    NOT NULL CHECK (reason IN ('EXPIRED', 'DAMAGED', 'LOST', 'OTHER')),
    notes          TEXT,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT fk_write_off_stock FOREIGN KEY (batch_id, warehouse_id)
        REFERENCES shelter_schema.batch_stock (batch_id, warehouse_id) ON DELETE RESTRICT
);

CREATE INDEX idx_write_off_stock ON shelter_schema.product_write_off (batch_id, warehouse_id);
CREATE INDEX idx_write_off_warehouse ON shelter_schema.product_write_off (warehouse_id);
CREATE INDEX idx_write_off_employee ON shelter_schema.product_write_off (employee_id);
CREATE INDEX idx_write_off_date ON shelter_schema.product_write_off (write_off_date);
