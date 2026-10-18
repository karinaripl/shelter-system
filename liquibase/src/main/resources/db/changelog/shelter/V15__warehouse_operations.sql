-- Минимальный остаток товара: ниже него товар попадает в отчёт «ниже минимума» (NULL — не отслеживается)
ALTER TABLE shelter_schema.product
    ADD COLUMN min_stock NUMERIC(10, 3)
        CONSTRAINT chk_product_min_stock CHECK (min_stock >= 0);

-- Перемещение остатка партии между складами. Общий остаток партии не меняется, меняется распределение
-- по строкам batch_stock; журнал нужен, чтобы остаток каждой строки сходился с историей операций
CREATE TABLE shelter_schema.stock_transfer (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id          BIGINT         NOT NULL,
    from_warehouse_id BIGINT         NOT NULL,
    to_warehouse_id   BIGINT         NOT NULL,
    employee_id       BIGINT         NOT NULL REFERENCES shelter_schema.employee (id) ON DELETE RESTRICT,
    quantity          NUMERIC(10, 3) NOT NULL CHECK (quantity > 0),
    transfer_date     DATE           NOT NULL DEFAULT CURRENT_DATE,
    notes             TEXT,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_transfer_different_warehouses CHECK (from_warehouse_id <> to_warehouse_id),
    CONSTRAINT fk_transfer_from_stock FOREIGN KEY (batch_id, from_warehouse_id)
        REFERENCES shelter_schema.batch_stock (batch_id, warehouse_id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_to_stock FOREIGN KEY (batch_id, to_warehouse_id)
        REFERENCES shelter_schema.batch_stock (batch_id, warehouse_id) ON DELETE RESTRICT
);

CREATE INDEX idx_transfer_from_stock ON shelter_schema.stock_transfer (batch_id, from_warehouse_id);
CREATE INDEX idx_transfer_to_stock ON shelter_schema.stock_transfer (batch_id, to_warehouse_id);
CREATE INDEX idx_transfer_from_warehouse ON shelter_schema.stock_transfer (from_warehouse_id);
CREATE INDEX idx_transfer_to_warehouse ON shelter_schema.stock_transfer (to_warehouse_id);
CREATE INDEX idx_transfer_employee ON shelter_schema.stock_transfer (employee_id);
CREATE INDEX idx_transfer_date ON shelter_schema.stock_transfer (transfer_date);

-- Склад, на который партия поступила (поставка или пожертвование). Вместе с журналами расхода, списания
-- и перемещений позволяет сверить остаток каждой строки batch_stock с историей операций
ALTER TABLE shelter_schema.product_batch
    ADD COLUMN received_warehouse_id BIGINT REFERENCES shelter_schema.warehouse (id) ON DELETE RESTRICT;

CREATE INDEX idx_batch_received_warehouse ON shelter_schema.product_batch (received_warehouse_id);

-- Для существующих партий склад поступления известен, только если партия лежит на одном складе
UPDATE shelter_schema.product_batch b
SET received_warehouse_id = s.warehouse_id
FROM (SELECT batch_id, MIN(warehouse_id) AS warehouse_id
      FROM shelter_schema.batch_stock
      GROUP BY batch_id
      HAVING COUNT(*) = 1) s
WHERE s.batch_id = b.id;
