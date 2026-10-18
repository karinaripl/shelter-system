-- Администратор не удаляется: он мог существовать до демо-данных
DELETE FROM users_schema.users WHERE email LIKE '%@demo.shelter.local';
