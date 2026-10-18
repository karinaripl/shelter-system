-- Медицинское состояние животного — отдельно от этапа жизненного цикла (статуса).
-- Ведёт ветеринар: здоров, карантин, лечение
ALTER TABLE shelter_schema.animal
    ADD COLUMN health_state VARCHAR(20) NOT NULL DEFAULT 'HEALTHY'
        CONSTRAINT chk_animal_health_state CHECK (health_state IN ('HEALTHY', 'QUARANTINE', 'TREATMENT'));

-- Хронические болезни и особые потребности — для сводки здоровья, которую видят все сотрудники
ALTER TABLE shelter_schema.medical_card
    ADD COLUMN chronic_conditions TEXT;

-- Этапы жизненного цикла: в приюте → готов к пристройству → забронирован → пристроен
INSERT INTO shelter_schema.animal_status (code, name, is_final) VALUES
    ('AVAILABLE', 'готов к пристройству', FALSE),
    ('RESERVED',  'забронирован',         FALSE);
