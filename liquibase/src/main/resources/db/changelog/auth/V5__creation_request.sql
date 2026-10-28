-- Ключ запроса, которым учётная запись создана через внутренний API (создание сотрудника в shelter-core-service).
-- Повторный запрос с тем же ключом возвращает ту же запись, а компенсирующее удаление удаляет только её
ALTER TABLE users_schema.users ADD COLUMN creation_request_id UUID UNIQUE;
