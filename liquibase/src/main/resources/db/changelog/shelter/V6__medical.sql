-- Медицинская карта: одна на животное. Флаги — текущее состояние
-- (могут быть отмечены при поступлении, если животное уже стерилизовано или привито)
CREATE TABLE shelter_schema.medical_card (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    animal_id           BIGINT      NOT NULL UNIQUE REFERENCES shelter_schema.animal (id) ON DELETE CASCADE,
    opened_on           DATE        NOT NULL DEFAULT CURRENT_DATE,
    is_sterilized       BOOLEAN     NOT NULL DEFAULT FALSE,
    is_vaccinated       BOOLEAN     NOT NULL DEFAULT FALSE,
    is_parasite_treated BOOLEAN     NOT NULL DEFAULT FALSE,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Запись приёма (осмотра). Вес хранится здесь, чтобы видеть его историю
CREATE TABLE shelter_schema.medical_record (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    medical_card_id BIGINT       NOT NULL REFERENCES shelter_schema.medical_card (id) ON DELETE CASCADE,
    employee_id     BIGINT       NOT NULL REFERENCES shelter_schema.employee (id) ON DELETE RESTRICT,
    record_date     DATE         NOT NULL DEFAULT CURRENT_DATE,
    weight_kg       NUMERIC(5, 2) CHECK (weight_kg > 0),
    diagnosis       TEXT,
    prescriptions   TEXT,
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_medical_record_card_date ON shelter_schema.medical_record (medical_card_id, record_date);
CREATE INDEX idx_medical_record_employee ON shelter_schema.medical_record (employee_id);

-- Проведённая процедура
CREATE TABLE shelter_schema.procedure_record (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    medical_card_id   BIGINT      NOT NULL REFERENCES shelter_schema.medical_card (id) ON DELETE CASCADE,
    procedure_type_id BIGINT      NOT NULL REFERENCES shelter_schema.procedure_type (id) ON DELETE RESTRICT,
    employee_id       BIGINT      NOT NULL REFERENCES shelter_schema.employee (id) ON DELETE RESTRICT,
    procedure_date    DATE        NOT NULL DEFAULT CURRENT_DATE,
    result            TEXT,
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_procedure_card_date ON shelter_schema.procedure_record (medical_card_id, procedure_date);
CREATE INDEX idx_procedure_type ON shelter_schema.procedure_record (procedure_type_id);
CREATE INDEX idx_procedure_employee ON shelter_schema.procedure_record (employee_id);

-- Норма расхода товара на одну процедуру данного вида
CREATE TABLE shelter_schema.procedure_consumption_rate (
    procedure_type_id BIGINT         NOT NULL REFERENCES shelter_schema.procedure_type (id) ON DELETE CASCADE,
    product_id        BIGINT         NOT NULL REFERENCES shelter_schema.product (id) ON DELETE RESTRICT,
    quantity          NUMERIC(10, 3) NOT NULL CHECK (quantity > 0),
    notes             TEXT,
    PRIMARY KEY (procedure_type_id, product_id)
);

CREATE INDEX idx_consumption_rate_product ON shelter_schema.procedure_consumption_rate (product_id);

-- Расход товара, сделанный в рамках процедуры (списание по норме)
ALTER TABLE shelter_schema.product_expense
    ADD COLUMN procedure_record_id BIGINT
        REFERENCES shelter_schema.procedure_record (id) ON DELETE RESTRICT;

CREATE INDEX idx_expense_procedure ON shelter_schema.product_expense (procedure_record_id);
