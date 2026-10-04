-- Мероприятие (выставка, день открытых дверей и т. п.)
CREATE TABLE shelter_schema.event (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(150) NOT NULL,
    event_date   DATE         NOT NULL,
    location     VARCHAR(200),
    organizer_id BIGINT       NOT NULL REFERENCES shelter_schema.employee (id) ON DELETE RESTRICT,
    notes        TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_event_date ON shelter_schema.event (event_date);
CREATE INDEX idx_event_organizer ON shelter_schema.event (organizer_id);

-- Участие животного в мероприятии
CREATE TABLE shelter_schema.event_participation (
    event_id        BIGINT      NOT NULL REFERENCES shelter_schema.event (id) ON DELETE CASCADE,
    animal_id       BIGINT      NOT NULL REFERENCES shelter_schema.animal (id) ON DELETE CASCADE,
    condition_after TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, animal_id)
);

CREATE INDEX idx_participation_animal ON shelter_schema.event_participation (animal_id);
