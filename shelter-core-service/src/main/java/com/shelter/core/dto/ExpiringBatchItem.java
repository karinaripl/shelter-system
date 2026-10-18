package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Партия с остатком на складе для отчётов «истекающие» и «к списанию». daysLeft < 0 — просрочена. */
public record ExpiringBatchItem(
        Long batchId,
        Long productId,
        String productName,
        String unit,
        Long warehouseId,
        String warehouseName,
        LocalDate expirationDate,
        long daysLeft,
        BigDecimal quantity
) {
}
