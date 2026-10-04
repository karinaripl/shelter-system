# Shelter System

Информационная система управления приютом для животных: внутренний учёт приюта
(животные, медицина, склад, волонтёры, финансы) и публичный сервис пристройства
(каталог, запись на знакомство, заявки). Выпускная квалификационная работа.

> **Текущее состояние: шаг 2 — схема базы данных приюта.** Готовы инфраструктура (шлюз,
> миграции, jOOQ, межсервисные вызовы), авторизация с ролями приюта и схема `shelter_schema`
> с демонстрационными данными. Предметные сервисы добавляются следующими шагами (см. «План»).

## Стек

Java 21 · Spring Boot 4 · Spring Cloud Gateway · OpenFeign · Spring Security + JWT ·
PostgreSQL · Liquibase · jOOQ · Docker Compose

## Модули

| Модуль | Порт | Назначение |
|---|---|---|
| `gateway-service` | 8766 | Единая точка входа: маршрутизация, проверка JWT, подпись заголовков пользователя |
| `auth-service` | 8180 | Регистрация и вход, выдача JWT, хеширование паролей (BCrypt) |
| `booking-service` | 8181 | Записи (будет перепрофилирован под визиты и заявки на пристройство) |
| `notification-service` | 8182 | Уведомления (пока пишет в лог) |
| `room-service` | 8183 | Временный демонстрационный сервис, будет заменён на `shelter-core-service` |
| `gateway-auth-starter` | — | Общий стартер: проверка подписи шлюза во внутренних сервисах |
| `model` | — | Сгенерированные jOOQ-классы |
| `liquibase` | — | Миграции схемы БД (в Docker запускается как одноразовый контейнер `migrations`) |

Клиенты обращаются **только к шлюзу** (`http://localhost:8766`). Пути `/internal/**`
предназначены для вызовов между сервисами и через шлюз недоступны.

## Требования

- Docker Desktop (или Docker Engine + Compose v2)

Для разработки в IDE дополнительно: JDK 21 и Maven 3.9+.

## Запуск всей системы одной командой

```bash
docker compose up --build
```

Что происходит:

1. собирается проект (Maven внутри Docker, на компьютере Java и Maven не нужны);
2. запускается PostgreSQL;
3. контейнер `migrations` накатывает миграции Liquibase и завершается;
4. запускаются `auth-service`, `room-service`, `notification-service`, `booking-service`;
5. запускается шлюз `gateway-service` на http://localhost:8766.

Первая сборка занимает несколько минут: Docker скачивает образы и Maven-зависимости.
Повторные сборки идут быстрее благодаря кэшу.

Полезные команды:

```bash
docker compose up --build -d          # запустить в фоне
docker compose ps                     # что запущено (migrations должен быть "Exited (0)")
docker compose logs -f auth-service   # логи одного сервиса
docker compose down                   # остановить, данные БД сохранятся
docker compose down -v                # остановить и удалить данные БД
```

Файл `.env` необязателен: без него используются значения по умолчанию из
`docker-compose.yml`. Чтобы поменять пароль БД, секрет JWT или данные первого администратора,
выполните `cp .env.example .env` и отредактируйте `.env`.

При первом запуске `auth-service` создаёт администратора из `ADMIN_EMAIL` / `ADMIN_PASSWORD`
(по умолчанию `admin@shelter.local` / `admin12345` — только для локального запуска).
Если в базе уже есть хотя бы один ADMIN, этот шаг пропускается.

## Роли и доступ

| Роль | Назначение |
|---|---|
| `ADMIN` | Учётные записи сотрудников и полный доступ |
| `VET` | Медицина |
| `CURATOR` | Животные, клетки, склад |
| `ADOPTION_MANAGER` | Визиты на знакомство и заявки на пристройство |
| `COORDINATOR` | Волонтёры, мероприятия, финансы |
| `VISITOR` | Публичная часть: каталог, запись на знакомство, заявки |

- У пользователя может быть несколько ролей, но хотя бы одна.
- Самостоятельная регистрация (`POST /auth/register`) всегда даёт только `VISITOR`.
  Сотрудников с нужными ролями создаёт администратор (`POST /auth/users`).
- Роли передаются в JWT списком (claim `roles`). Шлюз передаёт их сервисам в заголовке
  `X-User-Roles` через запятую и подписывает (`X-Gateway-Signature`). `gateway-auth-starter`
  превращает каждую роль в `ROLE_<роль>`, поэтому в сервисах работает `@PreAuthorize("hasRole('ADMIN')")`.
- Заголовки `X-User-*`, присланные клиентом, шлюз удаляет.

| Метод и путь | Доступ | Назначение |
|---|---|---|
| `POST /auth/register` | все | Регистрация посетителя, возвращает токен |
| `POST /auth/login` | все | Вход, возвращает токен |
| `GET /auth/me` | вошедший | Свой профиль и роли |
| `PUT /auth/me/password` | вошедший | Смена своего пароля |
| `GET /auth/users`, `GET /auth/users/{id}` | ADMIN | Пользователи |
| `POST /auth/users` | ADMIN | Создать пользователя со списком ролей |
| `PUT /auth/users/{id}/roles` | ADMIN | Заменить список ролей |
| `PATCH /auth/users/{id}/status` | ADMIN | Заблокировать / разблокировать (`{"enabled": false}`) |
| `GET /auth/roles` | ADMIN | Список ролей с описанием |

Администратор не может снять роль `ADMIN` с себя или заблокировать себя — так в системе
всегда остаётся хотя бы один администратор. Заблокированный пользователь не может войти;
уже выданный ему токен действует до истечения срока (24 часа).

## Режим разработки (сервисы из IDE)

В Docker запускаются только база и миграции, сервисы запускаются из IntelliJ IDEA
(Run для `*Application`) или через Maven:

```bash
docker compose up -d postgres migrations
mvn -DskipTests install
mvn -pl auth-service spring-boot:run    # и аналогично для остальных сервисов
```

Сервисы сами читают `.env` из корня проекта, переменные в IDE задавать не нужно.
Не запускайте один и тот же сервис одновременно в Docker и в IDE.

## Проверка, что всё работает

Сценарий: вход администратора → создание комнаты → регистрация посетителя →
посетитель не может создать комнату (403) → запрос без токена отклоняется (401).

**Windows PowerShell:**

```powershell
# вход первого администратора — сохраняем токен
$admin = (Invoke-RestMethod -Method Post http://localhost:8766/auth/login -ContentType 'application/json' `
  -Body '{"email":"admin@shelter.local","password":"admin12345"}').token
$auth = @{ Authorization = "Bearer $admin" }

# администратор создаёт комнату и сотрудника с двумя ролями
Invoke-RestMethod -Method Post http://localhost:8766/rooms -Headers $auth -ContentType 'application/json' `
  -Body '{"name":"Room 1","capacity":3,"location":"Floor 1"}'
Invoke-RestMethod -Method Post http://localhost:8766/auth/users -Headers $auth -ContentType 'application/json' `
  -Body '{"username":"vet","email":"vet@shelter.local","password":"vet-password","roles":["VET","CURATOR"]}'

# регистрация посетителя: всегда роль VISITOR
$visitor = (Invoke-RestMethod -Method Post http://localhost:8766/auth/register -ContentType 'application/json' `
  -Body '{"username":"guest","email":"guest@shelter.local","password":"guest-password"}').token
Invoke-RestMethod http://localhost:8766/auth/me -Headers @{ Authorization = "Bearer $visitor" }

# посетитель не может создать комнату — 403
try { Invoke-WebRequest -Method Post http://localhost:8766/rooms -Headers @{ Authorization = "Bearer $visitor" } `
  -ContentType 'application/json' -Body '{"name":"Room 2","capacity":1,"location":"Floor 2"}' }
catch { $_.Exception.Response.StatusCode.value__ }

# без токена шлюз должен ответить 401
try { Invoke-WebRequest http://localhost:8766/rooms } catch { $_.Exception.Response.StatusCode.value__ }
```

**bash (Linux, macOS, Git Bash):**

```bash
ADMIN=$(curl -s -X POST localhost:8766/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@shelter.local","password":"admin12345"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

curl -s -X POST localhost:8766/rooms -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"name":"Room 1","capacity":3,"location":"Floor 1"}'
curl -s -X POST localhost:8766/auth/users -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"username":"vet","email":"vet@shelter.local","password":"vet-password","roles":["VET","CURATOR"]}'

VISITOR=$(curl -s -X POST localhost:8766/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"guest","email":"guest@shelter.local","password":"guest-password"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -s localhost:8766/auth/me -H "Authorization: Bearer $VISITOR"

curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8766/rooms -H "Authorization: Bearer $VISITOR" \
  -H 'Content-Type: application/json' -d '{"name":"Room 2","capacity":1,"location":"Floor 2"}'   # 403
curl -s -o /dev/null -w '%{http_code}\n' localhost:8766/rooms   # 401
```

## База данных приюта

Схема `shelter_schema` (28 таблиц): животные и клетки, медицина, склад, сотрудники и волонтёры,
мероприятия, финансы. ER-диаграмма, описание таблиц, отличия от прототипа и бизнес-правила
для следующих шагов — в [docs/database.md](docs/database.md).

`docker compose up` загружает и демонстрационные данные: 13 кошек в разных статусах, клетки,
медкарты, склад с партиями (в том числе с истекающим сроком), волонтёров, мероприятия,
пожертвования. Чтобы их не загружать, укажите в `.env` `LIQUIBASE_CONTEXTS=default`
(на уже заполненной базе демо-данные останутся — пересоздайте её через `docker compose down -v`).

## Перегенерация jOOQ-классов

После изменения миграций (нужны JDK 21 и Maven на компьютере):

```bash
docker compose up -d --build postgres migrations
mvn -pl model -Pjooq-codegen generate-sources
```

Сгенерированные классы коммитятся в репозиторий.

## План

- [x] **Шаг 0.** Каркас: перенос, переименование в `com.shelter`, исправление шлюза и миграций, сборка без БД, запуск всей системы через `docker compose up --build`
- [x] **Шаг 1.** Авторизация: роли приюта, запрет самоназначения роли при регистрации, валидация, единый формат ошибок
- [x] **Шаг 2.** Схема БД приюта в Liquibase (перенос из прототипа) и генерация jOOQ
- [ ] **Шаг 3.** `shelter-core-service`: животные, клетки, статусы
- [ ] **Шаг 4.** Медицина: карты, процедуры, нормы расхода
- [ ] **Шаг 5.** Склад: поставки, партии, сроки годности, списание
- [ ] **Шаг 6.** Волонтёры, мероприятия, финансы
- [ ] **Шаг 7.** `booking-service` → визиты на знакомство и заявки на пристройство
- [ ] **Шаг 8.** Уведомления: e-mail / Telegram
- [ ] **Шаг 9.** Тесты, OpenAPI
- [ ] **Шаг 10.** Клиентская часть (React + TypeScript)
