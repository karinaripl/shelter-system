-- Какой флаг медкарты ставит процедура этого вида (NULL — никакой).
-- Код вместо поиска по названию: названия видов процедур редактируются
ALTER TABLE shelter_schema.procedure_type
    ADD COLUMN card_flag VARCHAR(20)
        CONSTRAINT chk_procedure_type_card_flag
            CHECK (card_flag IN ('STERILIZED', 'VACCINATED', 'PARASITE_TREATED'));

UPDATE shelter_schema.procedure_type SET card_flag = 'STERILIZED'       WHERE name IN ('Стерилизация', 'Кастрация');
UPDATE shelter_schema.procedure_type SET card_flag = 'VACCINATED'       WHERE name = 'Вакцинация';
UPDATE shelter_schema.procedure_type SET card_flag = 'PARASITE_TREATED' WHERE name IN ('Дегельминтизация', 'Обработка от блох');

-- Флаг, который поставила именно эта процедура (до неё он был FALSE). NULL — процедура флаг не меняла.
-- При удалении процедуры по нему решается, снимать ли флаг
ALTER TABLE shelter_schema.procedure_record
    ADD COLUMN flag_set_by_this VARCHAR(20)
        CONSTRAINT chk_procedure_record_flag_set
            CHECK (flag_set_by_this IN ('STERILIZED', 'VACCINATED', 'PARASITE_TREATED'));

-- У каждого животного есть медкарта (открывается при поступлении)
INSERT INTO shelter_schema.medical_card (animal_id, opened_on)
SELECT a.id, a.arrival_date
FROM shelter_schema.animal a
WHERE NOT EXISTS (SELECT 1 FROM shelter_schema.medical_card mc WHERE mc.animal_id = a.id);
