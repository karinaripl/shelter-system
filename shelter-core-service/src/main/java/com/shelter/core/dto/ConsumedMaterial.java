package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Материал, списанный по процедуре (или возвращённый на склад при её удалении). */
public record ConsumedMaterial(
        Long productId,
        String productName,
        String unit,
        Long batchId,
        Long warehouseId,
        String warehouseName,
        LocalDate expirationDate,
        BigDecimal quantity
) {
}
