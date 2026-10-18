DELETE FROM shelter_schema.animal_status WHERE code IN ('AVAILABLE', 'RESERVED');
ALTER TABLE shelter_schema.medical_card DROP COLUMN IF EXISTS chronic_conditions;
ALTER TABLE shelter_schema.animal DROP COLUMN IF EXISTS health_state;
