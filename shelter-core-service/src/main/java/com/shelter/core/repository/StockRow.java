package com.shelter.core.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Остаток партии на конкретном складе (строка batch_stock) вместе с данными партии и склада. */
public record StockRow(
        Long batchId,
        Long warehouseId,
        String warehouseName,
        Long productId,
        LocalDate receivedDate,
        LocalDate expirationDate,
        BigDecimal quantity
) {
}
