-- Демонстрационные данные к шагу 5 (контекст demo): минимальные остатки товаров и склад поступления партий.
-- Корма для котят и капель от блох меньше минимума — они видны в отчёте «ниже минимума»
UPDATE shelter_schema.product p
SET min_stock = v.min_stock, updated_at = now()
FROM (VALUES
    ('Корм для взрослых кошек', 20.000),
    ('Корм для котят',          10.000),
    ('Корм лечебный',            5.000),
    ('Наполнитель древесный',   20.000),
    ('Шприц 5 мл',              30.000),
    ('Капли от блох',           15.000)
) AS v(product_name, min_stock)
WHERE p.name = v.product_name;

-- Склад поступления для демо-партий, которые лежат на нескольких складах (V15 заполнил его только для партий
-- на одном складе). Партия поступила на основной склад (если её там нет — на склад с меньшим id),
-- а на остальные склады попала перемещением в день поступления. Количество перемещения — сколько оказалось
-- на втором складе с учётом расхода и списаний оттуда, чтобы остатки сходились с историей операций
WITH received AS (
    SELECT b.id AS batch_id,
           COALESCE((SELECT s.warehouse_id
                     FROM shelter_schema.batch_stock s
                     JOIN shelter_schema.warehouse w ON w.id = s.warehouse_id
                     WHERE s.batch_id = b.id AND w.name = 'Основной склад'),
                    (SELECT MIN(s.warehouse_id) FROM shelter_schema.batch_stock s WHERE s.batch_id = b.id)) AS warehouse_id
    FROM shelter_schema.product_batch b
    WHERE b.received_warehouse_id IS NULL
      AND EXISTS (SELECT 1 FROM shelter_schema.batch_stock s WHERE s.batch_id = b.id)
)
UPDATE shelter_schema.product_batch b
SET received_warehouse_id = r.warehouse_id
FROM received r
WHERE r.batch_id = b.id;

INSERT INTO shelter_schema.stock_transfer (batch_id, from_warehouse_id, to_warehouse_id, employee_id, quantity, transfer_date, notes)
SELECT s.batch_id, b.received_warehouse_id, s.warehouse_id,
       COALESCE(b.employee_id, (SELECT e.id FROM shelter_schema.employee e WHERE e.full_name = 'Фёдоров Максим Олегович')),
       s.quantity
           + (SELECT COALESCE(SUM(x.quantity), 0) FROM shelter_schema.product_expense x
              WHERE x.batch_id = s.batch_id AND x.warehouse_id = s.warehouse_id)
           + (SELECT COALESCE(SUM(o.quantity), 0) FROM shelter_schema.product_write_off o
              WHERE o.batch_id = s.batch_id AND o.warehouse_id = s.warehouse_id),
       b.received_date,
       'demo-step5: перемещение при приёмке'
FROM shelter_schema.batch_stock s
JOIN shelter_schema.product_batch b ON b.id = s.batch_id
WHERE s.warehouse_id <> b.received_warehouse_id
  AND NOT EXISTS (SELECT 1 FROM shelter_schema.stock_transfer t
                  WHERE t.batch_id = s.batch_id AND t.to_warehouse_id = s.warehouse_id)
  AND s.quantity
          + (SELECT COALESCE(SUM(x.quantity), 0) FROM shelter_schema.product_expense x
             WHERE x.batch_id = s.batch_id AND x.warehouse_id = s.warehouse_id)
          + (SELECT COALESCE(SUM(o.quantity), 0) FROM shelter_schema.product_write_off o
             WHERE o.batch_id = s.batch_id AND o.warehouse_id = s.warehouse_id) > 0;
