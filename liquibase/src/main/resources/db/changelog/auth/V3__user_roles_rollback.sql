ALTER TABLE users_schema.users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

UPDATE users_schema.users u
SET role = 'ADMIN'
WHERE EXISTS (SELECT 1 FROM users_schema.user_roles r WHERE r.user_id = u.id AND r.role = 'ADMIN');

ALTER TABLE users_schema.users DROP COLUMN enabled;

DROP TABLE users_schema.user_roles;
