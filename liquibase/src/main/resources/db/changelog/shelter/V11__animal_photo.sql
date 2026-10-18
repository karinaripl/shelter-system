-- Фотографии животного. Сами файлы лежат в хранилище сервиса (docker volume),
-- в базе — имя файла относительно каталога хранилища
CREATE TABLE shelter_schema.animal_photo (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    animal_id  BIGINT       NOT NULL REFERENCES shelter_schema.animal (id) ON DELETE CASCADE,
    file_path  VARCHAR(255) NOT NULL UNIQUE,
    sort_order INTEGER      NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    is_main    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_photo_animal ON shelter_schema.animal_photo (animal_id, sort_order);
-- Не больше одного главного фото у животного
CREATE UNIQUE INDEX uq_photo_main ON shelter_schema.animal_photo (animal_id) WHERE is_main;
