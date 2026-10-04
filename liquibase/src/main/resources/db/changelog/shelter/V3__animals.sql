-- Клетка (вольер)
CREATE TABLE shelter_schema.cage (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number     VARCHAR(10) NOT NULL UNIQUE,
    capacity   SMALLINT    NOT NULL CHECK (capacity > 0),
    cage_type  VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Животное. Поступление: дата и источник. Выбытие: дата, итоговый статус (is_final) и комментарий.
-- Выбывшее животное и животное на передержке клетку не занимают (cage_id = NULL)
CREATE TABLE shelter_schema.animal (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name              VARCHAR(100),
    gender            CHAR(1)     CHECK (gender IN ('M', 'F')),
    birth_date        DATE,
    color             VARCHAR(50),
    breed             VARCHAR(50),
    character         TEXT,
    special_marks     TEXT,
    status_id         BIGINT      NOT NULL REFERENCES shelter_schema.animal_status (id) ON DELETE RESTRICT,
    cage_id           BIGINT      REFERENCES shelter_schema.cage (id) ON DELETE RESTRICT,
    arrival_source_id BIGINT      NOT NULL REFERENCES shelter_schema.arrival_source (id) ON DELETE RESTRICT,
    arrival_date      DATE        NOT NULL DEFAULT CURRENT_DATE,
    departure_date    DATE,
    departure_note    TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_animal_departure_after_arrival CHECK (departure_date IS NULL OR departure_date >= arrival_date),
    CONSTRAINT chk_animal_birth_before_arrival CHECK (birth_date IS NULL OR birth_date <= arrival_date),
    CONSTRAINT chk_animal_departed_no_cage CHECK (departure_date IS NULL OR cage_id IS NULL),
    CONSTRAINT chk_animal_departure_note CHECK (departure_note IS NULL OR departure_date IS NOT NULL)
);

CREATE INDEX idx_animal_status ON shelter_schema.animal (status_id);
CREATE INDEX idx_animal_cage ON shelter_schema.animal (cage_id);
CREATE INDEX idx_animal_arrival_source ON shelter_schema.animal (arrival_source_id);
CREATE INDEX idx_animal_arrival_date ON shelter_schema.animal (arrival_date);
-- Частый фильтр: животные, которые сейчас в приюте
CREATE INDEX idx_animal_present ON shelter_schema.animal (id) WHERE departure_date IS NULL;

-- Закрепление волонтёра за животным. Повторное закрепление после окончания — новая запись
CREATE TABLE shelter_schema.volunteer_assignment (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    volunteer_id BIGINT      NOT NULL REFERENCES shelter_schema.volunteer (id) ON DELETE CASCADE,
    animal_id    BIGINT      NOT NULL REFERENCES shelter_schema.animal (id) ON DELETE CASCADE,
    start_date   DATE        NOT NULL DEFAULT CURRENT_DATE,
    end_date     DATE,
    notes        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_assignment_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_assignment_volunteer ON shelter_schema.volunteer_assignment (volunteer_id);
CREATE INDEX idx_assignment_animal ON shelter_schema.volunteer_assignment (animal_id);
-- Не больше одного действующего закрепления волонтёра за одним животным
CREATE UNIQUE INDEX uq_assignment_active ON shelter_schema.volunteer_assignment (volunteer_id, animal_id)
    WHERE end_date IS NULL;
