-- Ключ запроса (Idempotency-Key), которым карточка создана в админ-панели: повтор того же запроса
-- возвращает эту карточку, а не создаёт вторую (и не упирается в её же паспорт)
ALTER TABLE shelter_schema.employee ADD COLUMN creation_request_id UUID UNIQUE;
