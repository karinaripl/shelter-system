CREATE SCHEMA IF NOT EXISTS shelter_schema;

-- Статус животного. is_final = животное выбыло из приюта (пристроено, умерло, сбежало, передано)
CREATE TABLE shelter_schema.animal_status (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code     VARCHAR(30)  NOT NULL UNIQUE,
    name     VARCHAR(50)  NOT NULL UNIQUE,
    is_final BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Источник поступления животного в приют
CREATE TABLE shelter_schema.arrival_source (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- Вид ветеринарной или гигиенической процедуры
CREATE TABLE shelter_schema.procedure_type (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT
);

-- Должность сотрудника (кадровый справочник, не роль доступа)
CREATE TABLE shelter_schema.position (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100)   NOT NULL UNIQUE,
    base_salary NUMERIC(12, 2) NOT NULL CHECK (base_salary >= 0)
);

-- Категория товара на складе
CREATE TABLE shelter_schema.product_category (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);
