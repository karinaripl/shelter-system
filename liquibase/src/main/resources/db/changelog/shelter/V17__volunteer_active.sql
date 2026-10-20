-- Волонтёр, который больше не помогает приюту: не удаляется (у него есть история закреплений),
-- а выводится из списков. Неактивного нельзя закрепить за животным
ALTER TABLE shelter_schema.volunteer ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
