ALTER TABLE shelter_schema.product_batch DROP COLUMN IF EXISTS received_warehouse_id;
DROP TABLE IF EXISTS shelter_schema.stock_transfer;
ALTER TABLE shelter_schema.product DROP COLUMN IF EXISTS min_stock;
