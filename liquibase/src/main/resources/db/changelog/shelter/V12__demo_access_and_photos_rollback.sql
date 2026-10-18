DELETE FROM shelter_schema.animal_photo WHERE file_path LIKE 'demo-%';

UPDATE shelter_schema.medical_card SET chronic_conditions = NULL;

UPDATE shelter_schema.animal SET health_state = 'HEALTHY';

UPDATE shelter_schema.animal
SET status_id = (SELECT id FROM shelter_schema.animal_status WHERE code = 'IN_SHELTER')
WHERE status_id IN (SELECT id FROM shelter_schema.animal_status WHERE code IN ('AVAILABLE', 'RESERVED'));

UPDATE shelter_schema.employee SET user_id = NULL;
