UPDATE shelter_schema.product_batch
SET received_warehouse_id = NULL
WHERE id IN (SELECT batch_id FROM shelter_schema.stock_transfer WHERE notes = 'demo-step5: перемещение при приёмке');

DELETE FROM shelter_schema.stock_transfer WHERE notes = 'demo-step5: перемещение при приёмке';

UPDATE shelter_schema.product SET min_stock = NULL;
