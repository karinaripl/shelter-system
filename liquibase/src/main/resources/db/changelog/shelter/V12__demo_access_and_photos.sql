-- Демонстрационные данные к шагу 3 (контекст demo)

-- Связь демо-сотрудников с учётными записями auth-service (по email; директор — администратор системы)
UPDATE shelter_schema.employee e
SET user_id = u.id, updated_at = now()
FROM users_schema.users u
WHERE (e.full_name = 'Соколова Ирина Павловна' AND u.email = 'admin@shelter.local')
   OR (e.email = u.email AND u.email LIKE '%@demo.shelter.local');

-- Этапы жизненного цикла: часть животных готова к пристройству, одно забронировано
UPDATE shelter_schema.animal a
SET status_id = s.id, updated_at = now()
FROM (VALUES
    ('Барсик', 'AVAILABLE'),
    ('Мурка',  'AVAILABLE'),
    ('Рыжик',  'AVAILABLE'),
    ('Снежка', 'RESERVED')
) AS v(animal_name, status_code)
JOIN shelter_schema.animal_status s ON s.code = v.status_code
WHERE a.name = v.animal_name;

-- Медицинское состояние
UPDATE shelter_schema.animal SET health_state = 'QUARANTINE', updated_at = now() WHERE name = 'Уголёк';
UPDATE shelter_schema.animal SET health_state = 'TREATMENT',  updated_at = now() WHERE name = 'Рыжик';

-- Хронические болезни и особые потребности
UPDATE shelter_schema.medical_card mc
SET chronic_conditions = v.conditions, updated_at = now()
FROM (VALUES
    ('Пушок',  'Хронический гастрит: только лечебный корм'),
    ('Снежка', 'Пугливая: нужен спокойный дом без маленьких детей')
) AS v(animal_name, conditions)
JOIN shelter_schema.animal a ON a.name = v.animal_name
WHERE mc.animal_id = a.id;

-- Фото-заглушки (файлы входят в сервис и копируются в хранилище при запуске)
INSERT INTO shelter_schema.animal_photo (animal_id, file_path, sort_order, is_main)
SELECT a.id, v.file_path, v.sort_order, v.is_main
FROM (VALUES
    ('Барсик', 'demo-barsik-1.png', 0, TRUE),
    ('Барсик', 'demo-barsik-2.png', 1, FALSE),
    ('Мурка',  'demo-murka-1.png',  0, TRUE),
    ('Рыжик',  'demo-ryzhik-1.png', 0, TRUE),
    ('Снежка', 'demo-snezhka-1.png', 0, TRUE),
    ('Снежка', 'demo-snezhka-2.png', 1, FALSE),
    ('Луна',   'demo-luna-1.png',   0, TRUE)
) AS v(animal_name, file_path, sort_order, is_main)
JOIN shelter_schema.animal a ON a.name = v.animal_name;
