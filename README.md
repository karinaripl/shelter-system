# Shelter System

Информационная система управления приютом для животных: внутренний учёт приюта
(животные, медицина, склад, волонтёры, финансы) и публичный сервис пристройства
(каталог, запись на знакомство, заявки). Выпускная квалификационная работа.

> **Текущее состояние: шаг 6.2 — админ-панель: сотрудники и финансы в `shelter-core-service`.** Готовы
> инфраструктура, авторизация с ролями, схема `shelter_schema` с демо-данными, матрица доступа с закрытой
> админ-панелью `/admin/**`, учёт животных и клеток, фото и публичный каталог, медкарты, осмотры, процедуры
> со списанием материалов со склада по нормам расхода, склад: товары, поставки, натуральные пожертвования,
> расход, перемещения, списания и отчёты; волонтёры, закрепления, мероприятия, публичные анонсы и отчёт
> об эффективности; сотрудники с учётными записями и увольнением, должности, благотворители, денежные
> пожертвования, журнал операций и баланс, оплата поставок, зарплаты и финансовые отчёты.
> Остальное — следующими шагами (см. «План»).

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
| `shelter-core-service` | 8184 | Учёт приюта: животные, клетки, этапы, фото, публичный каталог, медицина, склад, волонтёры, мероприятия; админ-панель: сотрудники, финансы, зарплаты |
| `gateway-auth-starter` | — | Общий стартер: проверка подписи шлюза, правило `/admin/**` только для ADMIN, ошибки 401/403 в общем формате |
| `model` | — | Сгенерированные jOOQ-классы |
| `liquibase` | — | Миграции схемы БД (в Docker запускается как одноразовый контейнер `migrations`) |

Клиенты обращаются **только к шлюзу** (`http://localhost:8766`). Пути `/internal/**`
предназначены для вызовов между сервисами и через шлюз недоступны: например, `shelter-core-service`
создаёт и блокирует учётные записи сотрудников через `auth-service` (`/internal/users`, OpenFeign). auth-service
принимает `/internal/**` только с заголовком `X-Internal-Token` = `INTERNAL_API_TOKEN` (иначе 401); значение задаётся
в `.env` (см. `.env.example`), в `docker-compose.yml` есть значение по умолчанию только для локального запуска.

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
4. запускаются `auth-service`, `shelter-core-service`, `room-service`, `notification-service`, `booking-service`
   (фото животных хранятся в volume `shelter-photos`);
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

## Права доступа

Роли, полная матрица «раздел × роль», этапы жизненного цикла животного и кто их меняет —
в [docs/access.md](docs/access.md). Это единый источник правил для всех сервисов.

| Роль | Назначение |
|---|---|
| `ADMIN` | Админ-панель (учётные записи, сотрудники, зарплаты, финансы) и полный доступ |
| `VET` | Медицина и медицинское состояние животных |
| `CURATOR` | Животные, клетки, фото, склад |
| `ADOPTION_MANAGER` | Визиты, заявки, пристройство, фото, мероприятия |
| `COORDINATOR` | Волонтёры, закрепления, мероприятия |
| `VISITOR` | Публичный каталог и свои заявки |

Всё о сотрудниках и деньгах живёт под `/admin/**` и доступно только `ADMIN`: это проверяет шлюз
и ещё раз каждый сервис. Без токена открыты только регистрация, вход и `/public/**`.

**Демо-учётки — только для демонстрации** (создаются с контекстом `demo`):

| Email | Пароль | Роль |
|---|---|---|
| `admin@shelter.local` | `admin12345` | ADMIN (связан с карточкой директора) |
| `lebedev@demo.shelter.local`, `kozlova@demo.shelter.local` | `demo12345` | VET |
| `fedorov@demo.shelter.local` | `demo12345` | CURATOR |
| `orlova@demo.shelter.local` | `demo12345` | ADOPTION_MANAGER |
| `belova@demo.shelter.local` | `demo12345` | COORDINATOR |

- У пользователя может быть несколько ролей, но хотя бы одна.
- Самостоятельная регистрация (`POST /auth/register`) всегда даёт только `VISITOR`.
  Сотрудника с учётной записью и ролями администратор создаёт одним действием (`POST /admin/employees`).
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
| `GET /admin/users`, `GET /admin/users/{id}` | ADMIN | Пользователи |
| `POST /admin/users` | ADMIN | Создать пользователя со списком ролей |
| `PUT /admin/users/{id}/roles` | ADMIN | Заменить список ролей |
| `PATCH /admin/users/{id}/status` | ADMIN | Заблокировать / разблокировать (`{"enabled": false}`) |
| `GET /admin/roles` | ADMIN | Список ролей с описанием |
| `GET /admin/employees?search&positionId&status` | ADMIN | Сотрудники с должностями и окладами (`status`: `WORKING`, `TERMINATED`, `ALL`) |
| `GET /admin/employees/{id}` | ADMIN | Полная карточка: паспорт, адрес, оклад, учётная запись |
| `POST /admin/employees` (заголовок `Idempotency-Key`) | ADMIN | Сотрудник и учётная запись одним действием (или с `userId` существующей записи, или без входа) |
| `PUT /admin/employees/{id}` | ADMIN | Изменение карточки |
| `PUT /admin/employees/{id}/account`, `DELETE /admin/employees/{id}/account` | ADMIN | Привязка существующей учётной записи и отвязка |
| `POST /admin/employees/{id}/termination`, `DELETE /admin/employees/{id}/termination` | ADMIN | Увольнение (вход блокируется) и отмена ошибочного увольнения |
| `GET`, `POST /admin/positions`; `GET`, `PUT`, `DELETE /admin/positions/{id}` | ADMIN | Должности и оклады; должность с сотрудниками не удаляется |
| `GET /admin/benefactors?name`, `POST`; `GET`, `PUT`, `DELETE /admin/benefactors/{id}` | ADMIN | Благотворители с контактами; с пожертвованиями не удаляется |
| `GET /admin/donations?from&to&type&benefactorId`, `GET /admin/donations/{id}` | ADMIN | Пожертвования обоих видов (у натуральных — товары без цен) |
| `POST /admin/donations`, `DELETE /admin/donations/{id}` | ADMIN | Денежное пожертвование (приход `DONATION`); удаление по правилам вида |
| `GET /admin/finance/transactions?from&to&direction&category`, `GET …/{id}` | ADMIN | Журнал операций с итогами по фильтру |
| `POST /admin/finance/transactions`, `DELETE /admin/finance/transactions/{id}` | ADMIN | Ручные операции `OTHER` с комментарием |
| `GET /admin/finance/balance?date` | ADMIN | Баланс = приход − расход на конец дня |
| `GET /admin/supplies/unpaid` | ADMIN | Неоплаченные поставки с суммами |
| `POST /admin/supplies/{id}/payment`, `DELETE /admin/supplies/{id}/payment` | ADMIN | Оплата на сумму поставки и её отмена |
| `DELETE /admin/supplies/{id}` | ADMIN | Удаление поставки вместе с оплатой |
| `GET /admin/salaries/preview?month=ГГГГ-ММ` | ADMIN | Предварительный расчёт зарплаты (ничего не сохраняет) |
| `POST /admin/salaries`, `GET /admin/salaries/{month}`, `DELETE /admin/salaries/{month}` | ADMIN | Начисление за месяц (один раз), просмотр, отмена целиком |
| `GET /admin/reports/finance/categories`, `/monthly`, `/balance` (`from`, `to`) | ADMIN | Приход и расход по категориям, по месяцам, баланс на начало и конец периода |
| `GET /admin/reports/donations/benefactors?from&to` | ADMIN | Пожертвования по благотворителям |
| `GET /animal-statuses`, `GET /arrival-sources` | сотрудники | Справочники для форм |
| `GET /cages`, `GET /cages/{id}` | сотрудники | Клетки с заполненностью; карточка со списком животных |
| `POST /cages`, `PUT /cages/{id}`, `DELETE /cages/{id}` | ADMIN, CURATOR | Клетки |
| `GET /animals`, `GET /animals/{id}` | сотрудники | Список (фильтры, `page`, `size`, `sort=arrivalDate,desc`) и карточка |
| `POST /animals`, `PUT /animals/{id}`, `PUT /animals/{id}/cage` | ADMIN, CURATOR | Поступление, описание, перевод в клетку |
| `PUT /animals/{id}/status` | ADMIN, CURATOR | Этап жизненного цикла (`RESERVED` — только ADMIN) |
| `PUT /animals/{id}/health-state` | ADMIN, VET | Здоров / карантин / лечение |
| `POST /animals/{id}/departure` | ADMIN, CURATOR, VET (только «умер») | Выбытие |
| `GET /animals/{id}/health-summary` | сотрудники | Сводка здоровья: флаги, состояние, последний вес, хронические болезни, дата осмотра |
| `GET /animals/{id}/medical-card`, `PUT /animals/{id}/medical-card` | ADMIN, VET | Полная медкарта; изменение флагов, хронических болезней и заметок |
| `GET /animals/{id}/medical-records`, `POST /animals/{id}/medical-records` | ADMIN, VET | Осмотры (`page`, `size`): дата, вес, диагноз, назначения |
| `GET /medical-records/{id}`, `DELETE /medical-records/{id}` | ADMIN, VET | Осмотр |
| `GET /animals/{id}/procedures`, `POST /animals/{id}/procedures` | ADMIN, VET | Процедуры; внесение списывает материалы норм со склада (выбранные партии или FEFO) |
| `GET /procedures/{id}`, `DELETE /procedures/{id}` | ADMIN, VET | Процедура; удаление возвращает материалы и снимает флаг, поставленный этой процедурой |
| `GET /procedure-types`, `GET /procedure-types/{id}` | ADMIN, VET, CURATOR | Виды процедур с нормами расхода и флагом медкарты |
| `GET /procedure-types/{id}/materials` | ADMIN, VET, CURATOR | Подходящие партии по FEFO (первая — рекомендуемая) и просроченные отдельно |
| `POST /procedure-types`, `PUT /procedure-types/{id}`, `PUT /procedure-types/{id}/rates`, `DELETE /procedure-types/{id}` | ADMIN, VET | Виды процедур и нормы расхода (замена целиком); вид с записями не удаляется |
| `GET /product-categories`, `/warehouses`, `/suppliers` (и `/{id}`) | ADMIN, CURATOR, VET | Справочники склада |
| `POST`, `PUT /{id}`, `DELETE /{id}` для `/product-categories`, `/warehouses`, `/suppliers` | ADMIN, CURATOR | Изменение справочников; запись со ссылками не удаляется |
| `GET /products`, `GET /products/{id}` | ADMIN, CURATOR, VET | Товары (фильтры `categoryId`, `active`, `name`) |
| `GET /products/{id}/stock?warehouseId=` | ADMIN, CURATOR, VET | Остатки и подсказка: годные партии по FEFO (первая — рекомендуемая), просроченные отдельно |
| `POST /products`, `PUT /products/{id}`, `PUT /products/{id}/active`, `DELETE /products/{id}` | ADMIN, CURATOR | Товары, минимальный остаток, архив; товар с историей не удаляется |
| `GET /supplies`, `GET /supplies/{id}`, `POST /supplies`, `DELETE /supplies/{id}` | ADMIN, CURATOR | Поставки без оплаты; цены и сумму видит только ADMIN |
| `GET /goods-donations`, `GET /goods-donations/{id}`, `POST /goods-donations`, `DELETE /goods-donations/{id}` | ADMIN, CURATOR | Натуральные пожертвования |
| `GET /benefactors?name=` | ADMIN, CURATOR | Благотворители для формы — только имена |
| `GET /expenses`, `GET /expenses/{id}` | ADMIN, CURATOR, VET | Журнал расхода (фильтры, `page`, `size`) |
| `POST /expenses`, `DELETE /expenses/{id}` | ADMIN, CURATOR | Ручной расход (выбранная партия или FEFO); удаление возвращает количество в партию |
| `GET /stock-transfers`, `POST /stock-transfers` | чтение: + VET; создание: ADMIN, CURATOR | Перемещение остатка партии между складами |
| `GET /write-offs`, `GET /write-offs/{id}`, `POST /write-offs` | чтение: + VET; создание: ADMIN, CURATOR | Списание с причиной |
| `GET /write-offs/expired/preview`, `POST /write-offs/expired` | ADMIN, CURATOR | Предпросмотр и подтверждение «списать все просроченные» |
| `GET /reports/stock`, `/reports/expiring?days=30`, `/reports/expired`, `/reports/below-minimum`, `/reports/movements?productId&from&to` | ADMIN, CURATOR, VET | Отчёты склада без сумм денег |
| `GET /animals/{id}/photos`, `GET /animals/{id}/photos/{photoId}/file` | сотрудники | Фото |
| `POST /animals/{id}/photos`, `DELETE …/{photoId}`, `PUT …/{photoId}/main` | ADMIN, CURATOR, ADOPTION_MANAGER | Загрузка (JPG/PNG до 5 МБ), удаление, главное фото |
| `GET /volunteers?name&skills&includeInactive`, `GET /volunteers/{id}` | ADMIN, COORDINATOR; CURATOR — только имя и контакты | Волонтёры (`page`, `size`); поиск по навыкам — не для куратора |
| `POST /volunteers`, `PUT /volunteers/{id}`, `PUT /volunteers/{id}/active`, `DELETE /volunteers/{id}` | ADMIN, COORDINATOR | Анкета с паспортом; признак «активен»; волонтёр с закреплениями не удаляется |
| `GET /volunteers/{id}/assignments?active=`, `GET /animals/{id}/volunteers?active=`, `GET /volunteer-assignments/{id}` | ADMIN, COORDINATOR, CURATOR | Закрепления волонтёра; волонтёры животного (действующие и история) |
| `POST /volunteer-assignments`, `PUT /volunteer-assignments/{id}/close`, `DELETE /volunteer-assignments/{id}` | ADMIN, COORDINATOR | Закрепление за животным, закрытие датой окончания, удаление ошибочной записи |
| `GET /events?from&to`, `GET /events/{id}`, `GET /animals/{id}/events` | сотрудники | Мероприятия (`page`, `size`), карточка с участниками, мероприятия животного |
| `POST /events`, `PUT /events/{id}`, `DELETE /events/{id}` | ADMIN, ADOPTION_MANAGER, COORDINATOR | Мероприятия; перенос убирает выбывших (`removedAnimals` в ответе) |
| `POST /events/{id}/animals`, `PUT /events/{id}/animals/{animalId}`, `DELETE /events/{id}/animals/{animalId}` | ADMIN, ADOPTION_MANAGER, COORDINATOR | Участие животных, состояние после мероприятия |
| `GET /reports/event-effectiveness?from&to` | ADMIN, ADOPTION_MANAGER, COORDINATOR | Эффективность мероприятий: участники и доля пристроенных, итог |
| `GET /public/animals`, `GET /public/animals/{id}`, `GET /public/photos/{photoId}` | все, без токена | Публичный каталог |
| `GET /public/events` | все, без токена | Анонсы будущих мероприятий: название, дата, место |

**Процедуры и склад.** При внесении процедуры материалы её норм расхода списываются со склада в той же
транзакции. Партию выбирает сотрудник (`materials`: товар, партия, склад, количество — по умолчанию норма);
если список не передан, берутся партии, рекомендованные по FEFO (ближайший срок годности первым), и ответ
показывает, какие партии списаны (`consumedMaterials`, `materialSelection`). Просроченные партии для животных
не используются никогда (409 «партия просрочена, её нужно списать»), при нехватке — 409 со списком «нужно / есть»,
и ничего не сохраняется. Материалы вне норм по процедуре не списываются: это ручной расход склада, который
ветеринар не оформляет (docs/access.md, сноска ³). Вакцинация, стерилизация / кастрация и обработка от паразитов
ставят флаг медкарты; удаление процедуры возвращает материалы в те же партии и снимает флаг, только если его
поставила эта процедура и других процедур с этим флагом у животного нет.

**Склад.** Поставку оформляет куратор: позиции с количеством, ценой и сроком годности (позже даты прихода)
превращаются в партии с остатком на выбранном складе; поставка создаётся неоплаченной, оплату вносит
администратор в админ-панели. Цены и сумму поставки видит только ADMIN — для остальных этих полей нет в ответе,
в отчётах сумм нет ни для кого. Натуральное пожертвование — от благотворителя из списка (видны только имена),
нового по имени или анонимно. Ручной расход — из выбранной партии или по FEFO на складе; просроченную партию
использовать нельзя, её списывают (по одной или все сразу: предпросмотр → подтверждение с `token`; если остатки
за это время изменились — 409). Остаток не может стать отрицательным: строки остатков блокируются до конца
транзакции в общем порядке, а по каждой партии всегда верно «остаток = поступило − расход − списание».
Удаление ошибочного расхода возвращает количество в ту же партию; расход по процедуре удаляется только вместе
с процедурой. Товар с историей не удаляется, а переводится в архив: архивный товар не принимается на склад,
но его остатки можно расходовать и списывать.

**Волонтёры и мероприятия.** Анкету волонтёра с паспортом (4 + 6 цифр, оба поля или ни одного) ведут
координатор и администратор; куратор видит только имя и контакты. Волонтёра с закреплениями не удаляют,
а делают неактивным (сначала нужно закрыть действующие закрепления); неактивного нельзя закрепить за животным.
У пары волонтёр–животное не больше одного действующего закрепления; за выбывшим животным не закрепляют, а его
действующие закрепления закрывает выбытие. Мероприятие организует сотрудник (по умолчанию — текущий пользователь).
Участников можно внести заранее, до дня мероприятия включительно; в прошедшее мероприятие — только администратор.
Животное, выбывшее раньше даты мероприятия, добавить нельзя, а при переносе даты такие участники убираются
автоматически. Состояние после мероприятия указывается с его даты. Отчёт об эффективности считает долю
участников, пристроенных впоследствии (статус `ADOPTED`), только по мероприятиям не позже сегодняшнего дня.

**Сотрудники.** Карточка сотрудника хранится в `shelter_schema`, учётная запись — в `auth-service` (другая база,
общей транзакции нет). При создании сначала проверяются данные карточки (должность, даты, паспорт, роли — без
`VISITOR`), затем `auth-service` создаёт учётную запись по ключу запроса (`Idempotency-Key`), затем сохраняется
карточка. Если карточка не сохранилась, учётная запись удаляется компенсирующим вызовом. Повтор запроса с тем же
ключом возвращает ту же карточку (200) и подхватывает учётную запись, оставшуюся после неудачной компенсации, — дублей
нет. Занятое имя или email — 409 с сообщением `auth-service`, карточка не создаётся; `auth-service` недоступен — 503.
Увольнение: дата не раньше приёма и не в будущем, нельзя уволить себя, нельзя уволить датой раньше месяца,
за который начислена зарплата, и организатора мероприятий после даты увольнения; учётная запись блокируется.

**Деньги.** Баланс = приход − расход по журналу. Любой расход (ручной, оплата поставки, зарплата) и удаление
прихода не должны увести баланс в минус ни на дату операции, ни на любую следующую дату — иначе 409 с текущим
балансом. Такие операции берут общую блокировку «кассы» до конца транзакции, поэтому параллельные оплаты не уводят
баланс в минус вместе. Операции `DONATION`, `SALARY`, `PURCHASE` создаются и удаляются только через свои документы
(пожертвование, начисление, оплату), вручную — только `OTHER` с комментарием. Зарплата за месяц начисляется один
раз работавшим в этом месяце: оклад × часы в неделю / 40, принятым и уволенным в середине месяца — пропорционально
календарным дням («12 из 31 дней»); за будущий месяц нельзя; не хватает денег на всю сумму — 409 и ничего
не сохраняется. Месяц, за который платят, и дата выплаты разделены: операция `SALARY` датируется днём начисления
(сегодня), поэтому зарплату за прошлый месяц можно выплатить из денег, пришедших после его конца, а операций будущим
числом нет. Отчёты строятся по дате операции; в отчёте по категориям зарплата дополнительно разбита по месяцам,
за которые начислена. Все суммы — `NUMERIC(12,2)` / `BigDecimal`, два знака.

Администратор не может снять роль `ADMIN` с себя или заблокировать себя — так в системе
всегда остаётся хотя бы один администратор. Заблокированный (в том числе уволенный) пользователь не может войти;
уже выданный ему токен действует до истечения срока (8 часов, `JWT_EXPIRATION`).

## Режим разработки (сервисы из IDE)

В Docker запускаются только база и миграции, сервисы запускаются из IntelliJ IDEA
(Run для `*Application`) или через Maven:

```bash
docker compose up -d postgres migrations
mvn -DskipTests install
mvn -pl auth-service spring-boot:run    # и аналогично для остальных сервисов
```

Сервисы сами читают `.env` из корня проекта, переменные в IDE задавать не нужно. Для запуска из IDE в `.env`
должны быть `JWT_SECRET` и `INTERNAL_API_TOKEN` (без них auth-service и shelter-core-service не запустятся).
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
Invoke-RestMethod -Method Post http://localhost:8766/admin/users -Headers $auth -ContentType 'application/json' `
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
curl -s -X POST localhost:8766/admin/users -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"username":"vet","email":"vet@shelter.local","password":"vet-password","roles":["VET","CURATOR"]}'

VISITOR=$(curl -s -X POST localhost:8766/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"guest","email":"guest@shelter.local","password":"guest-password"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -s localhost:8766/auth/me -H "Authorization: Bearer $VISITOR"

curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8766/rooms -H "Authorization: Bearer $VISITOR" \
  -H 'Content-Type: application/json' -d '{"name":"Room 2","capacity":1,"location":"Floor 2"}'   # 403
curl -s -o /dev/null -w '%{http_code}\n' localhost:8766/rooms   # 401
```

### Postman-коллекции с автотестами

В `docs/postman`:

- `shelter-step1.postman_collection.json` — регистрация, вход, профиль, смена пароля, учётные записи (`/admin/users`);
- `shelter-step3.postman_collection.json` — сквозной сценарий шага 3 (сотрудники → клетки → поступление → фото →
  перевод → «готов к пристройству» → публичный каталог → выбытие) и проверки матрицы доступа и правил;
- `shelter-step4.postman_collection.json` — медицина на демо-учётках (ветеринар `lebedev`, куратор `fedorov`,
  координатор `belova`): осмотр с весом → вакцинация по FEFO → остаток партии уменьшился → флаг «привит» в медкарте
  и сводке → ручной выбор не-FEFO партии → просроченная партия (409) → нехватка материалов (409, остатки прежние) →
  удаление ошибочной кастрации снимает флаг, а у поступившего стерилизованным — нет → состояние здоровья;
  проверки правил и матрицы доступа;
- `shelter-step5.postman_collection.json` — склад: куратор заводит товар → поставка с двумя партиями (куратор не видит
  цен и суммы, администратор видит) → ручной расход с выбором не-FEFO партии → удаление расхода → перемещение →
  списание испорченного → просроченная партия (в расход нельзя) → предпросмотр и списание всех просроченных →
  натуральное пожертвование → отчёты; негативные проверки правил и матрицы доступа;
- `shelter-step6-1.postman_collection.json` — волонтёры и мероприятия (координатор `belova`, менеджер по пристройству
  `orlova`, куратор `fedorov`, администратор): волонтёр с паспортом → закрепление → повторное (409) → деактивация
  с действующим закреплением (409) → закрытие → куратор видит волонтёра без паспорта → неактивного не закрепить (409) →
  мероприятие на сегодня → участник → повторно (409) → состояние после → мероприятие на завтра: участник заранее (201) →
  вчерашнее мероприятие: менеджер — 409, администратор — 201 → перенос убирает выбывшее животное → пристройство после
  мероприятия → отчёт эффективности → `/public/events` без токена; негативные проверки матрицы доступа; в конце —
  уборка: из будущих мероприятий прогона убираются участники, сами мероприятия удаляются (в `/public/events`
  не копятся), прошедшее мероприятие с участниками остаётся для отчёта;
- `shelter-step6-2.postman_collection.json` — админ-панель (администратор, куратор `fedorov` и остальные демо-учётки):
  должность → сотрудник с учётной записью (повтор с тем же `Idempotency-Key` — та же карточка) → новый сотрудник
  входит и видит свою роль → денежное пожертвование → баланс вырос → куратор создаёт поставку → администратор видит
  её в неоплаченных и оплачивает → баланс уменьшился → повторная оплата (409) → оплата сверх баланса (409) →
  предварительный расчёт зарплаты → начисление → повтор (409) → увольнение → уволенный не может войти → отчёты;
  негативные проверки: каждая роль, кроме ADMIN, на `/admin/finance/...` — 403, без токена — 401, занятый username —
  409 и карточка не создана. В конце начисление за месяц отменяется. Если за текущий месяц зарплату уже начислили
  вручную, коллекция остановится на первом шаге с подсказкой (сама она чужое начисление не отменяет).

В Postman: Import → файл коллекции → Run. Для загрузки фото укажите рабочую папку `docs/postman`
(Settings → General → Working directory). Без установки Node.js коллекции запускаются через Docker:

```bash
for c in shelter-step1 shelter-step3 shelter-step4 shelter-step5 shelter-step6-1 shelter-step6-2; do
  docker run --rm -v "$PWD/docs/postman:/etc/newman" postman/newman:6-alpine \
    run $c.postman_collection.json --env-var baseUrl=http://host.docker.internal:8766
done
```

Коллекции можно запускать повторно: email, номера клеток и животные уникальны для каждого прогона.
Коллекциям шагов 4, 5, 6.1 и 6.2 нужны демо-учётки. Коллекция шага 6.2 при каждом прогоне оставляет
должность, уволенного сотрудника, благотворителя, пожертвование и оплаченную поставку (это история, их не удаляют),
поэтому баланс от прогона к прогону растёт. Коллекция шага 4 сама принимает поставку вакцины (просроченная,
ближняя и дальняя партии), поэтому не зависит от демо-остатков и от «списать все просроченные» шага 5.

### Автотесты

`mvn test` запускает юнит-тесты, `@WebMvcTest` матрицы доступа и интеграционные тесты `*IT`
на PostgreSQL в Testcontainers (без Docker интеграционные тесты пропускаются). Интеграционные тесты финансов
и сотрудников (`FinanceIT`, `EmployeeAccountIT`) поднимают весь `shelter-core-service`; `auth-service` в них
заменён WireMock.

## База данных приюта

Схема `shelter_schema` (30 таблиц): животные и клетки, медицина, склад, сотрудники и волонтёры,
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
- [x] **Шаг 3.** `shelter-core-service`: матрица доступа, животные, клетки, этапы, фото, публичный каталог, `/admin/employees`
- [x] **Шаг 4.** Медицина: медкарты, осмотры, процедуры, нормы расхода, списание материалов (FEFO / выбор партии), сводка здоровья
- [x] **Шаг 5.** Склад: товары и справочники, поставки (без оплаты), натуральные пожертвования, расход (FEFO / выбор партии), перемещения, списание (в том числе всех просроченных), отчёты
- [x] **Шаг 6.1.** Волонтёры (анкеты, признак «активен»), закрепления за животными, мероприятия и участие животных, публичные анонсы, отчёт об эффективности
- [x] **Шаг 6.2.** Админ-панель: сотрудники с учётными записями и увольнением, должности, благотворители, денежные пожертвования, журнал операций и баланс, оплата поставок, зарплаты, финансовые отчёты
- [ ] **Шаг 7.** `booking-service` → визиты на знакомство и заявки на пристройство
- [ ] **Шаг 8.** Уведомления: e-mail / Telegram
- [ ] **Шаг 9.** Тесты, OpenAPI
- [ ] **Шаг 10.** Клиентская часть (React + TypeScript)
