-- Демонстрационные данные к шагу 4 (контекст demo): три партии вакцины в отдельной поставке
-- (без оплаты — её вносит администратор на шаге 5). Сроки считаются от дня применения миграции:
-- просроченная (не предлагается и не может быть выбрана для животного, её списание — шаг 5),
-- с ближайшим сроком (FEFO рекомендует её) и с дальним сроком (можно выбрать вручную)
INSERT INTO shelter_schema.supply (supplier_id, supply_date, employee_id, notes)
SELECT s.id, CURRENT_DATE - 100, e.id, 'Вакцины (демо шага 4)'
FROM shelter_schema.supplier s, shelter_schema.employee e
WHERE s.name = 'ООО «ВетСнаб»' AND e.full_name = 'Лебедев Андрей Викторович';

INSERT INTO shelter_schema.product_batch (product_id, supply_id, received_date, expiration_date, quantity, unit_price, employee_id)
SELECT p.id, sup.id, sup.supply_date, v.expiration, v.quantity, 900.00, sup.employee_id
FROM (VALUES
    (CURRENT_DATE - 1,    5.000),
    (CURRENT_DATE + 30,  40.000),
    (CURRENT_DATE + 300, 60.000)
) AS v(expiration, quantity)
JOIN shelter_schema.supply sup ON sup.notes = 'Вакцины (демо шага 4)'
JOIN shelter_schema.product p ON p.name = 'Вакцина комплексная';

INSERT INTO shelter_schema.batch_stock (batch_id, warehouse_id, quantity)
SELECT b.id, w.id, b.quantity
FROM shelter_schema.product_batch b
JOIN shelter_schema.supply sup ON sup.id = b.supply_id AND sup.notes = 'Вакцины (демо шага 4)'
JOIN shelter_schema.warehouse w ON w.name = 'Медицинский склад';
