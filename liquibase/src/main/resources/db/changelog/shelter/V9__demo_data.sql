-- Демонстрационные данные (контекст demo). Все люди и организации вымышленные.
-- Даты считаются от дня применения миграции, поэтому «истекающий срок» остаётся актуальным

-- ---------- Сотрудники ----------
INSERT INTO shelter_schema.employee (position_id, full_name, phone, email, hire_date, termination_date, weekly_hours)
SELECT p.id, v.full_name, v.phone, v.email, CURRENT_DATE - v.hired_days_ago, CURRENT_DATE - v.left_days_ago, v.hours
FROM (VALUES
    ('Директор',                 'Соколова Ирина Павловна',   '89000000001', 'sokolova@demo.shelter.local', 2000, NULL::int, 40),
    ('Ветеринар',                'Лебедев Андрей Викторович', '89000000002', 'lebedev@demo.shelter.local',  1500, NULL,      40),
    ('Ветеринар',                'Козлова Наталья Сергеевна', '89000000003', 'kozlova@demo.shelter.local',   900, NULL,      40),
    ('Менеджер по пристройству', 'Орлова Дарья Игоревна',     '89000000004', 'orlova@demo.shelter.local',    700, NULL,      40),
    ('Администратор',            'Фёдоров Максим Олегович',   '89000000005', 'fedorov@demo.shelter.local',   600, NULL,      40),
    ('Координатор волонтёров',   'Белова Анна Романовна',     '89000000006', 'belova@demo.shelter.local',    500, NULL,      20),
    ('Уборщик',                  'Ершов Пётр Ильич',          '89000000007', 'ershov@demo.shelter.local',    800, 30,        40)
) AS v(position_name, full_name, phone, email, hired_days_ago, left_days_ago, hours)
JOIN shelter_schema.position p ON p.name = v.position_name;

-- ---------- Клетки ----------
INSERT INTO shelter_schema.cage (number, capacity, cage_type) VALUES
    ('A-01', 2, 'Маленькая'),
    ('A-02', 2, 'Маленькая'),
    ('B-01', 4, 'Средняя'),
    ('B-02', 4, 'Средняя'),
    ('C-01', 6, 'Большая'),
    ('D-01', 1, 'Карантин'),
    ('D-02', 1, 'Карантин'),
    ('F-01', 8, 'Вольер');

-- ---------- Животные: 13 кошек в разных статусах ----------
INSERT INTO shelter_schema.animal (name, gender, birth_date, color, breed, character, special_marks,
                                   status_id, cage_id, arrival_source_id, arrival_date, departure_date, departure_note)
SELECT v.name, v.gender, CURRENT_DATE - v.age_days, v.color, v.breed, v.character, v.marks,
       s.id, c.id, src.id, CURRENT_DATE - v.arrived_days_ago, CURRENT_DATE - v.departed_days_ago, v.note
FROM (VALUES
    ('Барсик',  'M', 1500, 'рыжий',           'беспородный',   'Ласковый, любит людей',          NULL,                     'IN_SHELTER',  'A-01', 'STREET',          200, NULL::int, NULL),
    ('Мурка',   'F', 1100, 'трёхцветная',     'беспородная',   'Спокойная',                      'Купированное ухо',       'IN_SHELTER',  'A-01', 'ABANDONED',       150, NULL,      NULL),
    ('Рыжик',   'M',  400, 'рыжий',           'беспородный',   'Игривый',                        NULL,                     'IN_SHELTER',  'B-01', 'STREET',           90, NULL,      NULL),
    ('Снежка',  'F',  700, 'белая',           'турецкая ангора', 'Пугливая, привыкает к людям',  'Голубые глаза',          'IN_SHELTER',  'B-01', 'FROM_OWNER',       60, NULL,      NULL),
    ('Тишка',   'M',  120, 'серый полосатый', 'беспородный',   'Активный котёнок',               NULL,                     'IN_SHELTER',  'B-01', 'BORN_IN_SHELTER', 120, NULL,      NULL),
    ('Уголёк',  'M',  900, 'чёрный',          'беспородный',   'Насторожен, на карантине',       'Шрам на носу',           'IN_SHELTER',  'D-01', 'STREET',            5, NULL,      NULL),
    ('Луна',    'F',  800, 'серая',           'британская',    'Независимая',                    NULL,                     'FOSTER',      NULL,   'OTHER_SHELTER',   100, NULL,      NULL),
    ('Пушок',   'M', 2500, 'белый',           'перс',          'Пожилой, нужен уход',            'Хронический гастрит',    'FOSTER',      NULL,   'FROM_OWNER',      300, NULL,      NULL),
    ('Маркиза', 'F', 1300, 'черепаховая',     'беспородная',   'Ласковая',                       NULL,                     'ADOPTED',     NULL,   'STREET',          400, 120,       'Пристроена в семью после знакомства на выставке'),
    ('Дымок',   'M',  600, 'дымчатый',        'беспородный',   'Общительный',                    NULL,                     'ADOPTED',     NULL,   'ABANDONED',       250,  20,       'Пристроен, договор подписан'),
    ('Ночка',   'F', 3600, 'чёрная',          'беспородная',   'Очень спокойная',                'Возрастная',             'DIED',        NULL,   'FROM_OWNER',      500, 200,       'Умерла от возрастных заболеваний'),
    ('Персик',  'M',  500, 'кремовый',        'беспородный',   'Любопытный',                     NULL,                     'ESCAPED',     NULL,   'STREET',          180, 160,       'Сбежал во время уборки вольера'),
    ('Соня',    'F', 1000, 'серо-белая',      'беспородная',   'Не ладит с другими кошками',     NULL,                     'TRANSFERRED', NULL,   'UNKNOWN',         220,  50,       'Передана в приют, где содержат кошек отдельно')
) AS v(name, gender, age_days, color, breed, character, marks, status_code, cage_number, source_code,
       arrived_days_ago, departed_days_ago, note)
JOIN shelter_schema.animal_status s ON s.code = v.status_code
JOIN shelter_schema.arrival_source src ON src.code = v.source_code
LEFT JOIN shelter_schema.cage c ON c.number = v.cage_number;

-- ---------- Волонтёры и закрепления ----------
INSERT INTO shelter_schema.volunteer (full_name, phone, email, registration_date, skills) VALUES
    ('Гусева Полина Андреевна',  '89100000001', 'guseva@demo.shelter.local',  CURRENT_DATE - 400, 'Фотосъёмка для анкет'),
    ('Тарасов Кирилл Денисович', '89100000002', 'tarasov@demo.shelter.local', CURRENT_DATE - 300, 'Социализация пугливых кошек'),
    ('Зайцева Мария Олеговна',   '89100000003', 'zaytseva@demo.shelter.local', CURRENT_DATE - 250, 'Передержка'),
    ('Комаров Илья Сергеевич',   '89100000004', 'komarov@demo.shelter.local', CURRENT_DATE - 100, 'Ремонт вольеров'),
    ('Попова Вера Николаевна',   '89100000005', 'popova@demo.shelter.local',  CURRENT_DATE - 30,  'Уход за пожилыми животными');

INSERT INTO shelter_schema.volunteer_assignment (volunteer_id, animal_id, start_date, end_date, notes)
SELECT vol.id, a.id, CURRENT_DATE - v.start_days_ago, CURRENT_DATE - v.end_days_ago, v.notes
FROM (VALUES
    ('Тарасов Кирилл Денисович', 'Снежка',  55, NULL::int, 'Приучение к рукам'),
    ('Зайцева Мария Олеговна',   'Луна',    95, NULL,      'Передержка дома'),
    ('Попова Вера Николаевна',   'Пушок',   25, NULL,      'Передержка, диета'),
    ('Гусева Полина Андреевна',  'Рыжик',   80, NULL,      'Фото для каталога'),
    ('Гусева Полина Андреевна',  'Дымок',  200, 20,        'Фото для каталога, животное пристроено'),
    ('Тарасов Кирилл Денисович', 'Мурка',  140, 100,       'Социализация завершена')
) AS v(volunteer_name, animal_name, start_days_ago, end_days_ago, notes)
JOIN shelter_schema.volunteer vol ON vol.full_name = v.volunteer_name
JOIN shelter_schema.animal a ON a.name = v.animal_name;

-- ---------- Финансы: благотворители и денежные пожертвования ----------
INSERT INTO shelter_schema.benefactor (full_name, phone, email, notes) VALUES
    ('ООО «Добрые лапы»',       '84950000001', 'info@demo-paws.local',   'Ежеквартальная помощь'),
    ('Смирнов Олег Петрович',   '89200000001', 'smirnov@demo.local',     NULL),
    ('Волкова Ксения Алексеевна', '89200000002', NULL,                   'Помогает кормом'),
    ('Фонд «Тёплый дом»',       '84950000002', 'fund@demo-warmhome.local', NULL);

INSERT INTO shelter_schema.donation (benefactor_id, donation_date, donation_type, notes)
SELECT b.id, CURRENT_DATE - v.days_ago, v.donation_type, v.notes
FROM (VALUES
    ('ООО «Добрые лапы»',        500, 'MONEY', 'Квартальный перевод'),
    ('Фонд «Тёплый дом»',         45, 'MONEY', 'Грант на стерилизацию'),
    ('Смирнов Олег Петрович',     20, 'MONEY', NULL),
    ('Волкова Ксения Алексеевна', 15, 'GOODS', 'Корм и наполнитель')
) AS v(benefactor_name, days_ago, donation_type, notes)
JOIN shelter_schema.benefactor b ON b.full_name = v.benefactor_name;

-- Анонимное денежное пожертвование
INSERT INTO shelter_schema.donation (benefactor_id, donation_date, donation_type, notes)
VALUES (NULL, CURRENT_DATE - 10, 'MONEY', 'Анонимно, через ящик для пожертвований');

-- Приход денег по денежным пожертвованиям
INSERT INTO shelter_schema.finance_transaction (transaction_date, direction, category, amount, description, donation_id)
SELECT d.donation_date, 'INCOME', 'DONATION', v.amount, 'Пожертвование', d.id
FROM shelter_schema.donation d
JOIN (VALUES
    ('Квартальный перевод',                    250000.00),
    ('Грант на стерилизацию',                  150000.00),
    ('Анонимно, через ящик для пожертвований',  15000.00)
) AS v(notes, amount) ON v.notes = d.notes
WHERE d.donation_type = 'MONEY';

INSERT INTO shelter_schema.finance_transaction (transaction_date, direction, category, amount, description, donation_id)
SELECT d.donation_date, 'INCOME', 'DONATION', 80000.00, 'Пожертвование', d.id
FROM shelter_schema.donation d
JOIN shelter_schema.benefactor b ON b.id = d.benefactor_id
WHERE b.full_name = 'Смирнов Олег Петрович' AND d.donation_type = 'MONEY';

-- Прочий расход
INSERT INTO shelter_schema.finance_transaction (transaction_date, direction, category, amount, description)
VALUES (CURRENT_DATE - 40, 'EXPENSE', 'OTHER', 12000.00, 'Ремонт вольера F-01');

-- Зарплата за прошлый месяц работающим сотрудникам: ставка × часы в неделю / 40
WITH emp AS (
    SELECT e.id, e.full_name, ROUND(p.base_salary * e.weekly_hours / 40, 2) AS amount
    FROM shelter_schema.employee e
    JOIN shelter_schema.position p ON p.id = e.position_id
    WHERE e.termination_date IS NULL
),
tx AS (
    INSERT INTO shelter_schema.finance_transaction (transaction_date, direction, category, amount, description)
    SELECT (date_trunc('month', CURRENT_DATE) - INTERVAL '1 day')::date, 'EXPENSE', 'SALARY', emp.amount,
           'Зарплата: ' || emp.full_name
    FROM emp
    RETURNING id, description
)
INSERT INTO shelter_schema.salary_payment (transaction_id, employee_id, period_month)
SELECT tx.id, emp.id, date_trunc('month', CURRENT_DATE - INTERVAL '1 month')::date
FROM tx
JOIN emp ON tx.description = 'Зарплата: ' || emp.full_name;

-- ---------- Склад: справочные данные ----------
INSERT INTO shelter_schema.product (name, category_id, unit_of_measure)
SELECT v.name, c.id, v.unit
FROM (VALUES
    ('Корм для взрослых кошек', 'Корм',        'кг'),
    ('Корм для котят',          'Корм',        'кг'),
    ('Корм лечебный',           'Корм',        'кг'),
    ('Наполнитель древесный',   'Наполнитель', 'кг'),
    ('Вакцина комплексная',     'Медицина',    'доза'),
    ('Шприц 5 мл',              'Медицина',    'шт'),
    ('Антисептик',              'Медицина',    'мл'),
    ('Глистогонное',            'Медицина',    'таб'),
    ('Капли от блох',           'Медицина',    'шт'),
    ('Миска',                   'Инвентарь',   'шт'),
    ('Лежанка',                 'Инвентарь',   'шт'),
    ('Когтеточка',              'Инвентарь',   'шт')
) AS v(name, category_name, unit)
JOIN shelter_schema.product_category c ON c.name = v.category_name;

INSERT INTO shelter_schema.warehouse (name, warehouse_type, capacity) VALUES
    ('Основной склад',    'Корма и инвентарь', 1500),
    ('Медицинский склад', 'Медикаменты',        500),
    ('Карантинный блок',  'Запас для карантина', 200);

INSERT INTO shelter_schema.supplier (name, phone, email, address) VALUES
    ('ООО «ЗооОпт»',  '84951110001', 'sales@demo-zooopt.local', 'г. Москва, ул. Складская, д. 1'),
    ('ООО «ВетСнаб»', '84951110002', 'order@demo-vetsnab.local', 'г. Москва, ул. Аптечная, д. 7'),
    ('ИП «Корм-Сервис»', '84951110003', NULL,                    'Московская обл., г. Химки');

-- Поставки с оплатой (операция PURCHASE)
WITH tx AS (
    INSERT INTO shelter_schema.finance_transaction (transaction_date, direction, category, amount, description)
    VALUES (CURRENT_DATE - 400, 'EXPENSE', 'PURCHASE', 9000.00, 'Закупка лечебного корма')
    RETURNING id
)
INSERT INTO shelter_schema.supply (supplier_id, supply_date, employee_id, transaction_id, notes)
SELECT s.id, CURRENT_DATE - 400, e.id, tx.id, 'Лечебный корм'
FROM tx, shelter_schema.supplier s, shelter_schema.employee e
WHERE s.name = 'ИП «Корм-Сервис»' AND e.full_name = 'Фёдоров Максим Олегович';

WITH tx AS (
    INSERT INTO shelter_schema.finance_transaction (transaction_date, direction, category, amount, description)
    VALUES (CURRENT_DATE - 70, 'EXPENSE', 'PURCHASE', 20000.00, 'Закупка кормов и наполнителя')
    RETURNING id
)
INSERT INTO shelter_schema.supply (supplier_id, supply_date, employee_id, transaction_id, notes)
SELECT s.id, CURRENT_DATE - 70, e.id, tx.id, 'Корма и наполнитель'
FROM tx, shelter_schema.supplier s, shelter_schema.employee e
WHERE s.name = 'ООО «ЗооОпт»' AND e.full_name = 'Фёдоров Максим Олегович';

WITH tx AS (
    INSERT INTO shelter_schema.finance_transaction (transaction_date, direction, category, amount, description)
    VALUES (CURRENT_DATE - 100, 'EXPENSE', 'PURCHASE', 17200.00, 'Закупка медикаментов')
    RETURNING id
)
INSERT INTO shelter_schema.supply (supplier_id, supply_date, employee_id, transaction_id, notes)
SELECT s.id, CURRENT_DATE - 100, e.id, tx.id, 'Вакцины и расходники'
FROM tx, shelter_schema.supplier s, shelter_schema.employee e
WHERE s.name = 'ООО «ВетСнаб»' AND e.full_name = 'Лебедев Андрей Викторович';

-- ---------- Партии ----------
-- Из поставок: среди них просроченная (лечебный корм) и истекающая через неделю (вакцина)
INSERT INTO shelter_schema.product_batch (product_id, supply_id, received_date, expiration_date, quantity, unit_price, employee_id)
SELECT p.id, sup.id, sup.supply_date, sup.supply_date + v.shelf_days, v.quantity, v.price, sup.employee_id
FROM (VALUES
    ('Лечебный корм',              'Корм лечебный',           395,   20.000,  450.00),
    ('Корма и наполнитель',        'Корм для взрослых кошек', 365,   50.000,  300.00),
    ('Корма и наполнитель',        'Корм для котят',          300,   10.000,  350.00),
    ('Корма и наполнитель',        'Наполнитель древесный',   NULL,  40.000,   37.50),
    ('Вакцины и расходники',       'Вакцина комплексная',     107,   15.000,  900.00),
    ('Вакцины и расходники',       'Шприц 5 мл',             1000,  100.000,   10.00),
    ('Вакцины и расходники',       'Антисептик',              500, 1000.000,    1.50),
    ('Вакцины и расходники',       'Глистогонное',            365,   30.000,   25.00),
    ('Вакцины и расходники',       'Капли от блох',           730,   10.000,   45.00)
) AS v(supply_notes, product_name, shelf_days, quantity, price)
JOIN shelter_schema.supply sup ON sup.notes = v.supply_notes
JOIN shelter_schema.product p ON p.name = v.product_name;

-- Из натурального пожертвования (без цены)
INSERT INTO shelter_schema.product_batch (product_id, donation_id, received_date, expiration_date, quantity, employee_id)
SELECT p.id, d.id, d.donation_date, d.donation_date + v.shelf_days, v.quantity, e.id
FROM (VALUES
    ('Корм для взрослых кошек', 240,  15.000),
    ('Наполнитель древесный',   NULL, 20.000),
    ('Лежанка',                 NULL,  3.000)
) AS v(product_name, shelf_days, quantity)
JOIN shelter_schema.product p ON p.name = v.product_name
CROSS JOIN shelter_schema.donation d
CROSS JOIN shelter_schema.employee e
WHERE d.donation_type = 'GOODS' AND e.full_name = 'Фёдоров Максим Олегович';

-- ---------- Остатки по складам (поступило − расход − списание) ----------
INSERT INTO shelter_schema.batch_stock (batch_id, warehouse_id, quantity)
SELECT b.id, w.id, v.quantity
FROM (VALUES
    ('Корм лечебный',           'supply',   'Основной склад',     6.000),
    ('Корм для взрослых кошек', 'supply',   'Основной склад',    30.000),
    ('Корм для взрослых кошек', 'supply',   'Карантинный блок',   8.000),
    ('Корм для котят',          'supply',   'Основной склад',     7.500),
    ('Наполнитель древесный',   'supply',   'Основной склад',    25.000),
    ('Вакцина комплексная',     'supply',   'Медицинский склад', 12.000),
    ('Шприц 5 мл',              'supply',   'Медицинский склад', 93.000),
    ('Антисептик',              'supply',   'Медицинский склад', 996.000),
    ('Глистогонное',            'supply',   'Медицинский склад', 29.000),
    ('Капли от блох',           'supply',   'Медицинский склад',  9.000),
    ('Корм для взрослых кошек', 'donation', 'Основной склад',    15.000),
    ('Наполнитель древесный',   'donation', 'Основной склад',    20.000),
    ('Лежанка',                 'donation', 'Основной склад',     3.000)
) AS v(product_name, source, warehouse_name, quantity)
JOIN shelter_schema.product p ON p.name = v.product_name
JOIN shelter_schema.product_batch b ON b.product_id = p.id
    AND ((v.source = 'supply' AND b.supply_id IS NOT NULL) OR (v.source = 'donation' AND b.donation_id IS NOT NULL))
JOIN shelter_schema.warehouse w ON w.name = v.warehouse_name;

-- ---------- Медицина ----------
INSERT INTO shelter_schema.medical_card (animal_id, opened_on, is_sterilized, is_vaccinated, is_parasite_treated, notes)
SELECT a.id, a.arrival_date, v.sterilized, v.vaccinated, v.parasite_treated, v.notes
FROM (VALUES
    ('Барсик',  TRUE,  TRUE,  TRUE,  NULL),
    ('Мурка',   TRUE,  TRUE,  TRUE,  NULL),
    ('Рыжик',   FALSE, TRUE,  TRUE,  'Стерилизация запланирована'),
    ('Снежка',  TRUE,  FALSE, TRUE,  'Поступила стерилизованной'),
    ('Тишка',   FALSE, TRUE,  TRUE,  NULL),
    ('Уголёк',  FALSE, FALSE, FALSE, 'Карантин 14 дней'),
    ('Луна',    TRUE,  TRUE,  TRUE,  NULL),
    ('Пушок',   TRUE,  TRUE,  TRUE,  'Лечебная диета'),
    ('Маркиза', TRUE,  TRUE,  TRUE,  NULL),
    ('Дымок',   TRUE,  TRUE,  TRUE,  NULL),
    ('Ночка',   TRUE,  TRUE,  TRUE,  NULL),
    ('Персик',  FALSE, TRUE,  TRUE,  NULL),
    ('Соня',    TRUE,  TRUE,  FALSE, NULL)
) AS v(animal_name, sterilized, vaccinated, parasite_treated, notes)
JOIN shelter_schema.animal a ON a.name = v.animal_name;

INSERT INTO shelter_schema.medical_record (medical_card_id, employee_id, record_date, weight_kg, diagnosis, prescriptions)
SELECT mc.id, e.id, CURRENT_DATE - v.days_ago, v.weight, v.diagnosis, v.prescriptions
FROM (VALUES
    ('Барсик', 'Лебедев Андрей Викторович', 195, 4.80, 'Здоров',                     NULL),
    ('Барсик', 'Лебедев Андрей Викторович',  30, 5.10, 'Здоров',                     NULL),
    ('Мурка',  'Козлова Наталья Сергеевна', 145, 3.60, 'Отит',                       'Ушные капли 7 дней'),
    ('Мурка',  'Козлова Наталья Сергеевна', 130, 3.70, 'Отит вылечен',               NULL),
    ('Рыжик',  'Лебедев Андрей Викторович',  88, 3.20, 'Здоров',                     'Плановая стерилизация'),
    ('Снежка', 'Козлова Наталья Сергеевна',  58, 3.40, 'Стресс после смены хозяина', 'Тихое место, наблюдение'),
    ('Тишка',  'Лебедев Андрей Викторович',  60, 1.20, 'Здоров',                     NULL),
    ('Уголёк', 'Козлова Наталья Сергеевна',   4, 4.50, 'Подозрение на лишай',        'Карантин, повторный осмотр через 7 дней'),
    ('Пушок',  'Лебедев Андрей Викторович', 290, 5.90, 'Хронический гастрит',        'Лечебный корм'),
    ('Пушок',  'Лебедев Андрей Викторович',  40, 5.60, 'Состояние стабильное',       'Продолжать диету')
) AS v(animal_name, vet_name, days_ago, weight, diagnosis, prescriptions)
JOIN shelter_schema.animal a ON a.name = v.animal_name
JOIN shelter_schema.medical_card mc ON mc.animal_id = a.id
JOIN shelter_schema.employee e ON e.full_name = v.vet_name;

INSERT INTO shelter_schema.procedure_record (medical_card_id, procedure_type_id, employee_id, procedure_date, result)
SELECT mc.id, pt.id, e.id, CURRENT_DATE - v.days_ago, v.result
FROM (VALUES
    ('Барсик', 'Вакцинация',        'Лебедев Андрей Викторович', 190, 'Без осложнений'),
    ('Барсик', 'Кастрация',         'Лебедев Андрей Викторович', 180, 'Успешно'),
    ('Барсик', 'Дегельминтизация',  'Козлова Наталья Сергеевна',  30, NULL),
    ('Мурка',  'Чистка ушей',       'Козлова Наталья Сергеевна', 145, NULL),
    ('Мурка',  'Стерилизация',      'Лебедев Андрей Викторович', 120, 'Успешно'),
    ('Рыжик',  'Вакцинация',        'Лебедев Андрей Викторович',  85, 'Без осложнений'),
    ('Рыжик',  'Обработка от блох', 'Козлова Наталья Сергеевна',  85, NULL),
    ('Снежка', 'Анализ крови',      'Козлова Наталья Сергеевна',  57, 'Норма'),
    ('Тишка',  'Вакцинация',        'Лебедев Андрей Викторович',  40, 'Без осложнений'),
    ('Тишка',  'Стрижка когтей',    'Козлова Наталья Сергеевна',  10, NULL),
    ('Уголёк', 'Осмотр',            'Козлова Наталья Сергеевна',   4, 'Взят соскоб'),
    ('Пушок',  'Лечение зубов',     'Лебедев Андрей Викторович', 200, 'Удалён один зуб'),
    ('Луна',   'Вакцинация',        'Козлова Наталья Сергеевна',  95, 'Без осложнений')
) AS v(animal_name, procedure_name, vet_name, days_ago, result)
JOIN shelter_schema.animal a ON a.name = v.animal_name
JOIN shelter_schema.medical_card mc ON mc.animal_id = a.id
JOIN shelter_schema.procedure_type pt ON pt.name = v.procedure_name
JOIN shelter_schema.employee e ON e.full_name = v.vet_name;

-- Нормы расхода
INSERT INTO shelter_schema.procedure_consumption_rate (procedure_type_id, product_id, quantity, notes)
SELECT pt.id, p.id, v.quantity, v.notes
FROM (VALUES
    ('Вакцинация',        'Вакцина комплексная', 1.000, 'Одна доза'),
    ('Вакцинация',        'Шприц 5 мл',          1.000, NULL),
    ('Вакцинация',        'Антисептик',          2.000, 'Обработка места укола'),
    ('Стерилизация',      'Шприц 5 мл',          3.000, NULL),
    ('Стерилизация',      'Антисептик',         10.000, NULL),
    ('Кастрация',         'Шприц 5 мл',          2.000, NULL),
    ('Кастрация',         'Антисептик',          8.000, NULL),
    ('Дегельминтизация',  'Глистогонное',        1.000, 'Одна таблетка на 5 кг веса'),
    ('Обработка от блох', 'Капли от блох',       1.000, NULL)
) AS v(procedure_name, product_name, quantity, notes)
JOIN shelter_schema.procedure_type pt ON pt.name = v.procedure_name
JOIN shelter_schema.product p ON p.name = v.product_name;

-- ---------- Расход и списание ----------
-- Расход по процедурам (по нормам) и общий расход
INSERT INTO shelter_schema.product_expense (batch_id, warehouse_id, employee_id, animal_id, quantity, expense_date, notes, procedure_record_id)
SELECT b.id, w.id, e.id, a.id, v.quantity, CURRENT_DATE - v.days_ago, v.notes, pr.id
FROM (VALUES
    ('Вакцина комплексная', 'Медицинский склад', 'Лебедев Андрей Викторович', 'Рыжик', 'Вакцинация',        1.000, 85, 'По норме'),
    ('Шприц 5 мл',          'Медицинский склад', 'Лебедев Андрей Викторович', 'Рыжик', 'Вакцинация',        1.000, 85, 'По норме'),
    ('Антисептик',          'Медицинский склад', 'Лебедев Андрей Викторович', 'Рыжик', 'Вакцинация',        2.000, 85, 'По норме'),
    ('Капли от блох',       'Медицинский склад', 'Козлова Наталья Сергеевна', 'Рыжик', 'Обработка от блох', 1.000, 85, 'По норме'),
    ('Вакцина комплексная', 'Медицинский склад', 'Лебедев Андрей Викторович', 'Тишка', 'Вакцинация',        1.000, 40, 'По норме'),
    ('Шприц 5 мл',          'Медицинский склад', 'Лебедев Андрей Викторович', 'Тишка', 'Вакцинация',        1.000, 40, 'По норме'),
    ('Антисептик',          'Медицинский склад', 'Лебедев Андрей Викторович', 'Тишка', 'Вакцинация',        2.000, 40, 'По норме'),
    ('Глистогонное',        'Медицинский склад', 'Козлова Наталья Сергеевна', 'Барсик', 'Дегельминтизация', 1.000, 30, 'По норме'),
    ('Вакцина комплексная', 'Медицинский склад', 'Козлова Наталья Сергеевна', 'Луна',  'Вакцинация',        1.000, 95, 'Вакцина из первой поставки'),
    ('Корм лечебный',       'Основной склад',    'Фёдоров Максим Олегович',   'Пушок', NULL,               10.000, 60, 'Диета'),
    ('Корм для котят',      'Основной склад',    'Фёдоров Максим Олегович',   'Тишка', NULL,                2.500, 30, NULL),
    ('Корм для взрослых кошек', 'Основной склад', 'Фёдоров Максим Олегович',  NULL,    NULL,               12.000, 20, 'Общее кормление'),
    ('Наполнитель древесный',   'Основной склад', 'Фёдоров Максим Олегович',  NULL,    NULL,               15.000, 15, 'Все вольеры')
) AS v(product_name, warehouse_name, employee_name, animal_name, procedure_name, quantity, days_ago, notes)
JOIN shelter_schema.product p ON p.name = v.product_name
JOIN shelter_schema.product_batch b ON b.product_id = p.id AND b.supply_id IS NOT NULL
JOIN shelter_schema.warehouse w ON w.name = v.warehouse_name
JOIN shelter_schema.employee e ON e.full_name = v.employee_name
LEFT JOIN shelter_schema.animal a ON a.name = v.animal_name
LEFT JOIN shelter_schema.medical_card mc ON mc.animal_id = a.id
LEFT JOIN shelter_schema.procedure_type pt ON pt.name = v.procedure_name
LEFT JOIN shelter_schema.procedure_record pr
    ON pr.medical_card_id = mc.id AND pr.procedure_type_id = pt.id AND pr.procedure_date = CURRENT_DATE - v.days_ago;

-- Списания: часть просроченного лечебного корма и повреждённые шприцы
INSERT INTO shelter_schema.product_write_off (batch_id, warehouse_id, employee_id, quantity, write_off_date, reason, notes)
SELECT b.id, w.id, e.id, v.quantity, CURRENT_DATE - v.days_ago, v.reason, v.notes
FROM (VALUES
    ('Корм лечебный', 'Основной склад',    4.000, 3, 'EXPIRED', 'Вскрытые упаковки с истёкшим сроком'),
    ('Шприц 5 мл',    'Медицинский склад', 5.000, 7, 'DAMAGED', 'Повреждена упаковка')
) AS v(product_name, warehouse_name, quantity, days_ago, reason, notes)
JOIN shelter_schema.product p ON p.name = v.product_name
JOIN shelter_schema.product_batch b ON b.product_id = p.id AND b.supply_id IS NOT NULL
JOIN shelter_schema.warehouse w ON w.name = v.warehouse_name
CROSS JOIN shelter_schema.employee e
WHERE e.full_name = 'Фёдоров Максим Олегович';

-- ---------- Мероприятия ----------
INSERT INTO shelter_schema.event (name, event_date, location, organizer_id, notes)
SELECT v.name, CURRENT_DATE + v.days_from_now, v.location, e.id, v.notes
FROM (VALUES
    ('Выставка-пристройство «Кот в дом»', -130, 'ТЦ «Радуга», 1 этаж', 'Орлова Дарья Игоревна', 'Пристроено 1 животное'),
    ('День открытых дверей',               14, 'Приют',               'Белова Анна Романовна', 'Набор волонтёров')
) AS v(name, days_from_now, location, organizer_name, notes)
JOIN shelter_schema.employee e ON e.full_name = v.organizer_name;

INSERT INTO shelter_schema.event_participation (event_id, animal_id, condition_after)
SELECT ev.id, a.id, v.condition_after
FROM (VALUES
    ('Маркиза', 'Спокойна, нашла хозяев'),
    ('Барсик',  'Устал, но в порядке'),
    ('Мурка',   'Немного нервничала')
) AS v(animal_name, condition_after)
JOIN shelter_schema.animal a ON a.name = v.animal_name
JOIN shelter_schema.event ev ON ev.name = 'Выставка-пристройство «Кот в дом»';
