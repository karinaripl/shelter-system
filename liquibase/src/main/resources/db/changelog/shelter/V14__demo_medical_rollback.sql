DELETE FROM shelter_schema.product_expense
WHERE batch_id IN (SELECT b.id FROM shelter_schema.product_batch b
                   JOIN shelter_schema.supply s ON s.id = b.supply_id
                   WHERE s.notes = 'Вакцины (демо шага 4)');
DELETE FROM shelter_schema.product_batch
WHERE supply_id IN (SELECT id FROM shelter_schema.supply WHERE notes = 'Вакцины (демо шага 4)');
DELETE FROM shelter_schema.supply WHERE notes = 'Вакцины (демо шага 4)';
