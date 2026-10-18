-- ТОЛЬКО ДЛЯ ДЕМОНСТРАЦИИ (контекст demo). Учётные записи демо-сотрудников приюта.
-- Пароль у демо-сотрудников — demo12345, у администратора — admin12345 (как ADMIN_PASSWORD по умолчанию).
-- Если администратор уже создан при запуске auth-service, запись не меняется (ON CONFLICT DO NOTHING)
INSERT INTO users_schema.users (username, email, password_hash) VALUES
    ('admin',   'admin@shelter.local',        '$2a$10$ut.J2OuaYBSVFrtdNTr66eDir5B2E/lVrMNA3LNnPcsBoKfkuJiNS'),
    ('lebedev', 'lebedev@demo.shelter.local', '$2a$10$kanezoBS9QfDtzGnJW1Py.WOYImoliie/2csiExm4uzI2tXaA2Tu.'),
    ('kozlova', 'kozlova@demo.shelter.local', '$2a$10$kanezoBS9QfDtzGnJW1Py.WOYImoliie/2csiExm4uzI2tXaA2Tu.'),
    ('fedorov', 'fedorov@demo.shelter.local', '$2a$10$kanezoBS9QfDtzGnJW1Py.WOYImoliie/2csiExm4uzI2tXaA2Tu.'),
    ('orlova',  'orlova@demo.shelter.local',  '$2a$10$kanezoBS9QfDtzGnJW1Py.WOYImoliie/2csiExm4uzI2tXaA2Tu.'),
    ('belova',  'belova@demo.shelter.local',  '$2a$10$kanezoBS9QfDtzGnJW1Py.WOYImoliie/2csiExm4uzI2tXaA2Tu.')
ON CONFLICT DO NOTHING;

INSERT INTO users_schema.user_roles (user_id, role)
SELECT u.id, v.role
FROM (VALUES
    ('admin@shelter.local',        'ADMIN'),
    ('lebedev@demo.shelter.local', 'VET'),
    ('kozlova@demo.shelter.local', 'VET'),
    ('fedorov@demo.shelter.local', 'CURATOR'),
    ('orlova@demo.shelter.local',  'ADOPTION_MANAGER'),
    ('belova@demo.shelter.local',  'COORDINATOR')
) AS v(email, role)
JOIN users_schema.users u ON u.email = v.email
ON CONFLICT DO NOTHING;
