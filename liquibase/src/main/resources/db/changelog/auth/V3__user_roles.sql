-- Роли пользователя: у одного пользователя может быть несколько ролей, но хотя бы одна
CREATE TABLE users_schema.user_roles (
    user_id BIGINT      NOT NULL REFERENCES users_schema.users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT chk_user_roles_role CHECK (role IN ('ADMIN', 'VET', 'CURATOR', 'ADOPTION_MANAGER', 'COORDINATOR', 'VISITOR'))
);

-- Переносим роли из старой колонки: USER становится VISITOR
INSERT INTO users_schema.user_roles (user_id, role)
SELECT id, CASE role WHEN 'ADMIN' THEN 'ADMIN' ELSE 'VISITOR' END
FROM users_schema.users;

ALTER TABLE users_schema.users DROP COLUMN role;

-- Заблокированный пользователь не может войти
ALTER TABLE users_schema.users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
