package com.shelter.core.dto;

import java.math.BigDecimal;

/** Товар, годный остаток которого меньше минимального. shortage — сколько не хватает до минимума. */
public record BelowMinimumItem(
        Long productId,
        String productName,
        String unit,
        BigDecimal minStock,
        BigDecimal usableQuantity,
        BigDecimal shortage
) {
}
