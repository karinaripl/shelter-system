-- Сотрудник приюта. Учётная запись и роли доступа живут в auth-service:
-- user_id ссылается на users_schema.users(id) без внешнего ключа между схемами
CREATE TABLE shelter_schema.employee (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT UNIQUE,
    position_id      BIGINT       NOT NULL REFERENCES shelter_schema.position (id) ON DELETE RESTRICT,
    full_name        VARCHAR(150) NOT NULL,
    phone            VARCHAR(20)  CHECK (phone ~ '^(\+7|8)[0-9]{10}$'),
    email            VARCHAR(255) CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    address          TEXT,
    birth_date       DATE,
    hire_date        DATE         NOT NULL DEFAULT CURRENT_DATE,
    termination_date DATE,
    weekly_hours     SMALLINT     NOT NULL DEFAULT 40 CHECK (weekly_hours BETWEEN 0 AND 168),
    passport_series  VARCHAR(4)   CHECK (passport_series ~ '^[0-9]{4}$'),
    passport_number  VARCHAR(6)   CHECK (passport_number ~ '^[0-9]{6}$'),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_employee_passport UNIQUE (passport_series, passport_number),
    CONSTRAINT chk_employee_passport_pair CHECK ((passport_series IS NULL) = (passport_number IS NULL)),
    CONSTRAINT chk_employee_termination CHECK (termination_date IS NULL OR termination_date >= hire_date),
    CONSTRAINT chk_employee_birth CHECK (birth_date IS NULL OR birth_date < hire_date)
);

CREATE INDEX idx_employee_position ON shelter_schema.employee (position_id);
-- Частый фильтр: работающие сотрудники (расчёт зарплаты, назначения)
CREATE INDEX idx_employee_active ON shelter_schema.employee (id) WHERE termination_date IS NULL;

-- Волонтёр
CREATE TABLE shelter_schema.volunteer (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name         VARCHAR(150) NOT NULL,
    phone             VARCHAR(20)  CHECK (phone ~ '^(\+7|8)[0-9]{10}$'),
    email             VARCHAR(255) CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    address           TEXT,
    birth_date        DATE,
    registration_date DATE         NOT NULL DEFAULT CURRENT_DATE,
    skills            TEXT,
    passport_series   VARCHAR(4)   CHECK (passport_series ~ '^[0-9]{4}$'),
    passport_number   VARCHAR(6)   CHECK (passport_number ~ '^[0-9]{6}$'),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_volunteer_passport UNIQUE (passport_series, passport_number),
    CONSTRAINT chk_volunteer_passport_pair CHECK ((passport_series IS NULL) = (passport_number IS NULL)),
    CONSTRAINT chk_volunteer_birth CHECK (birth_date IS NULL OR birth_date < registration_date)
);
