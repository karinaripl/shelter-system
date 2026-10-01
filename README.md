# Shelter System

Информационная система управления приютом для животных: внутренний учёт приюта
(животные, медицина, склад, волонтёры, финансы) и публичный сервис пристройства
(каталог, запись на знакомство, заявки). Выпускная квалификационная работа.

> **Текущее состояние: шаг 0 — каркас.** Инфраструктура (шлюз, авторизация, миграции,
> jOOQ, межсервисные вызовы) перенесена из проекта booking-service и приведена
> в рабочее состояние. Предметные сервисы приюта добавляются следующими шагами (см. «План»).

## Стек

Java 21 · Spring Boot 4 · Spring Cloud Gateway · OpenFeign · Spring Security + JWT ·
PostgreSQL · Liquibase · jOOQ · Docker Compose

## Модули

| Модуль | Порт | Назначение |
|---|---|---|
| `gateway-service` | 8765 | Единая точка входа: маршрутизация, проверка JWT, подпись заголовков пользователя |
| `auth-service` | 8080 | Регистрация и вход, выдача JWT, хеширование паролей (BCrypt) |
| `booking-service` | 8081 | Записи (будет перепрофилирован под визиты и заявки на пристройство) |
| `notification-service` | 8082 | Уведомления (пока пишет в лог) |
| `room-service` | 8083 | Временный демонстрационный сервис, будет заменён на `shelter-core-service` |
| `gateway-auth-starter` | — | Общий стартер: проверка подписи шлюза во внутренних сервисах |
| `model` | — | Сгенерированные jOOQ-классы |
| `liquibase` | — | Миграции схемы БД |

Клиенты обращаются **только к шлюзу** (`http://localhost:8765`). Пути `/internal/**`
предназначены для вызовов между сервисами и через шлюз недоступны.

## Требования

- JDK 21
- Maven 3.9+
- Docker (для PostgreSQL)

## Запуск

```bash
# 1. Настройки окружения
cp .env.example .env            # при желании поменяйте JWT_SECRET (≥ 32 символов)

# 2. База данных
docker compose up -d

# 3. Миграции
mvn -pl liquibase liquibase:update

# 4. Сборка (база для сборки не нужна)
mvn -DskipTests install

# 5. Сервисы — каждый в своём терминале или через конфигурации запуска IDE
mvn -pl auth-service spring-boot:run
mvn -pl room-service spring-boot:run
mvn -pl notification-service spring-boot:run
mvn -pl booking-service spring-boot:run
mvn -pl gateway-service spring-boot:run
```

Сервисы сами читают `.env` из корня проекта, задавать переменные в IDE не нужно.

## Проверка, что всё работает

```bash
# регистрация (сейчас роль можно передать в запросе — это будет закрыто на шаге 1)
curl -s -X POST localhost:8765/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"admin","email":"admin@shelter.local","password":"admin123","role":"ADMIN"}'

# вход — вернёт {"token": "..."}
TOKEN=$(curl -s -X POST localhost:8765/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@shelter.local","password":"admin123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

# запрос через шлюз с токеном
curl -s -X POST localhost:8765/rooms -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Комната 1","capacity":3,"location":"1 этаж"}'
curl -s localhost:8765/rooms -H "Authorization: Bearer $TOKEN"

# без токена шлюз должен ответить 401
curl -s -o /dev/null -w '%{http_code}\n' localhost:8765/rooms
```

## Перегенерация jOOQ-классов

После изменения миграций (база должна быть запущена и миграции применены):

```bash
mvn -pl liquibase liquibase:update
mvn -pl model -Pjooq-codegen generate-sources
```

Сгенерированные классы коммитятся в репозиторий.

## План

- [x] **Шаг 0.** Каркас: перенос, переименование в `com.shelter`, исправление шлюза и миграций, сборка без БД, docker-compose
- [ ] **Шаг 1.** Авторизация: роли приюта, запрет самоназначения роли при регистрации, валидация, единый формат ошибок
- [ ] **Шаг 2.** Схема БД приюта в Liquibase (перенос из прототипа) и генерация jOOQ
- [ ] **Шаг 3.** `shelter-core-service`: животные, клетки, статусы
- [ ] **Шаг 4.** Медицина: карты, процедуры, нормы расхода
- [ ] **Шаг 5.** Склад: поставки, партии, сроки годности, списание
- [ ] **Шаг 6.** Волонтёры, мероприятия, финансы
- [ ] **Шаг 7.** `booking-service` → визиты на знакомство и заявки на пристройство
- [ ] **Шаг 8.** Уведомления: e-mail / Telegram
- [ ] **Шаг 9.** Тесты, OpenAPI, Dockerfile для каждого сервиса
- [ ] **Шаг 10.** Клиентская часть (React + TypeScript)
